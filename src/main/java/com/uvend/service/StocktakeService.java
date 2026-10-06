package com.uvend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uvend.domain.entity.*;
import com.uvend.domain.enums.*;
import com.uvend.domain.repo.*;
import com.uvend.event.DomainEvent;
import com.uvend.event.EventPublisher;
import com.uvend.event.Topics;
import com.uvend.web.dto.StocktakeRequest;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * 盘点服务：
 * 1) 建单时按在柜库存逐货道逐批次快照系统数量
 * 2) 录入实盘数量，计算差异（实际-系统）
 * 3) 差异处置：LOSS（盘亏报损，批次可结算）、FOUND_MISPLACED（找到错放）、SYSTEM_CORRECTED（按实盘调账）
 * 4) 全部处理后关单
 */
@Service
public class StocktakeService {

    private final StocktakeRepository stocktakeRepository;
    private final StocktakeItemRepository itemRepository;
    private final MachineRepository machineRepository;
    private final SlotRepository slotRepository;
    private final BatchRepository batchRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryService inventoryService;
    private final LossService lossService;
    private final EventPublisher eventPublisher;
    private final Numbers numbers;
    private final ObjectMapper mapper;

    public StocktakeService(StocktakeRepository stocktakeRepository, StocktakeItemRepository itemRepository,
                            MachineRepository machineRepository, SlotRepository slotRepository,
                            BatchRepository batchRepository, InventoryRepository inventoryRepository,
                            InventoryService inventoryService, LossService lossService,
                            EventPublisher eventPublisher, Numbers numbers, ObjectMapper mapper) {
        this.stocktakeRepository = stocktakeRepository;
        this.itemRepository = itemRepository;
        this.machineRepository = machineRepository;
        this.slotRepository = slotRepository;
        this.batchRepository = batchRepository;
        this.inventoryRepository = inventoryRepository;
        this.inventoryService = inventoryService;
        this.lossService = lossService;
        this.eventPublisher = eventPublisher;
        this.numbers = numbers;
        this.mapper = mapper;
    }

    @Transactional
    public Map<String, Object> open(String machineCode, Long operatorId, String note) {
        Machine machine = findMachine(machineCode);
        Stocktake st = new Stocktake();
        st.setStocktakeNo(numbers.next("ST"));
        st.setMachineId(machine.getId());
        st.setOperatorId(operatorId);
        st.setStatus(StocktakeStatus.OPEN);
        st.setNote(note);
        stocktakeRepository.save(st);

        List<Inventory> invs = inventoryRepository.findByMachineId(machine.getId());
        List<Map<String, Object>> lines = new ArrayList<>();
        for (Inventory inv : invs) {
            if (inv.getQty() == 0) continue;
            StocktakeItem si = new StocktakeItem();
            si.setStocktakeId(st.getId());
            si.setSlotId(inv.getSlotId());
            si.setBatchId(inv.getBatchId());
            si.setSystemQty(inv.getQty());
            si.setState(StocktakeItemState.PENDING);
            itemRepository.save(si);
            lines.add(view(si));
        }
        publish(st, "StocktakeOpened", Map.of("lines", lines.size()));
        return Map.of("stocktakeNo", st.getStocktakeNo(), "status", st.getStatus().name(), "lines", lines);
    }

    @Transactional
    public Map<String, Object> count(String stocktakeNo, List<StocktakeRequest.CountLine> counts) {
        Stocktake st = getStocktake(stocktakeNo);
        ensureOpen(st);
        List<StocktakeItem> items = itemRepository.findByStocktakeId(st.getId());
        List<Map<String, Object>> results = new ArrayList<>();

        for (StocktakeRequest.CountLine line : counts) {
            Slot slot = slotRepository.findByMachineIdAndCode(st.getMachineId(), line.slotCode())
                    .orElseThrow(() -> new EntityNotFoundException("货道不存在: " + line.slotCode()));
            Long batchId = null;
            if (line.batchNo() != null && !line.batchNo().isBlank()) {
                Batch b = findBatch(line.batchNo());
                batchId = b.getId();
            }
            final Long fb = batchId;
            StocktakeItem si = items.stream()
                    .filter(i -> i.getSlotId().equals(slot.getId())
                            && ((fb == null && i.getBatchId() == null) || Objects.equals(i.getBatchId(), fb)))
                    .findFirst().orElse(null);

            if (si == null) {
                // 实盘出系统中不存在的批次（典型：补货员放错货道/账外商品）
                si = new StocktakeItem();
                si.setStocktakeId(st.getId());
                si.setSlotId(slot.getId());
                si.setBatchId(batchId);
                si.setSystemQty(0);
            }
            si.setActualQty(line.actualQty());
            si.setDiffQty(line.actualQty() - si.getSystemQty());
            si.setState(si.getDiffQty() == 0 ? StocktakeItemState.MATCH : StocktakeItemState.MISMATCH);
            itemRepository.save(si);
            results.add(view(si));
        }
        return Map.of("stocktakeNo", stocktakeNo, "counted", results.size(), "items", results);
    }

    @Transactional
    public Map<String, Object> resolve(String stocktakeNo, Long itemId, String resolution, String note) {
        Stocktake st = getStocktake(stocktakeNo);
        ensureOpen(st);
        StocktakeItem si = itemRepository.findById(itemId)
                .orElseThrow(() -> new EntityNotFoundException("盘点行不存在: " + itemId));
        if (si.getState() != StocktakeItemState.MISMATCH) {
            throw new IllegalStateException("仅差异行需要处置");
        }
        StocktakeResolution r = StocktakeResolution.valueOf(resolution);
        int diff = si.getDiffQty() == null ? 0 : si.getDiffQty();
        if (si.getBatchId() == null) {
            throw new IllegalStateException("该行缺少批次信息，请先在盘点中补录 batchNo 后再处置");
        }

        switch (r) {
            case LOSS -> {
                if (diff >= 0) throw new IllegalArgumentException("盘盈不能按报损处置");
                int lossQty = -diff;
                inventoryService.apply(st.getMachineId(), si.getSlotId(), si.getBatchId(), -lossQty,
                        LedgerReason.STOCKTAKE_ADJUST, "STOCKTAKE", st.getId());
                LossRecord loss = lossService.record(si.getBatchId(), st.getMachineId(), si.getSlotId(),
                        lossQty, LossReason.STOCKTAKE_DIFF, "STOCKTAKE", st.getId());
                si.setResolution(StocktakeResolution.LOSS);
                si.setState(StocktakeItemState.RESOLVED);
                itemRepository.save(si);
                publish(st, "StocktakeLoss", Map.of(
                        "itemId", itemId, "qty", lossQty, "lossNo", loss.getLossNo()));
                return Map.of("itemId", itemId, "resolution", r.name(), "lossNo", loss.getLossNo());
            }
            case FOUND_MISPLACED, SYSTEM_CORRECTED -> {
                // 按实盘数量调账（盘盈入库或盘亏调减，不记供应商损耗）
                inventoryService.apply(st.getMachineId(), si.getSlotId(), si.getBatchId(), diff,
                        LedgerReason.STOCKTAKE_ADJUST, "STOCKTAKE", st.getId());
                si.setResolution(r);
                si.setState(StocktakeItemState.RESOLVED);
                itemRepository.save(si);
                publish(st, "StocktakeAdjusted", Map.of("itemId", itemId, "diff", diff, "resolution", r.name()));
                return Map.of("itemId", itemId, "resolution", r.name(), "adjustedDiff", diff);
            }
        }
        throw new IllegalArgumentException("未知处置: " + resolution);
    }

    @Transactional
    public Map<String, Object> close(String stocktakeNo) {
        Stocktake st = getStocktake(stocktakeNo);
        ensureOpen(st);
        List<StocktakeItem> items = itemRepository.findByStocktakeId(st.getId());
        List<StocktakeItem> pending = items.stream()
                .filter(i -> i.getState() == StocktakeItemState.PENDING
                        || i.getState() == StocktakeItemState.MISMATCH).toList();
        if (!pending.isEmpty()) {
            throw new IllegalStateException("仍有 " + pending.size() + " 行未盘点或差异未处置，不能关单");
        }
        st.setStatus(StocktakeStatus.CLOSED);
        st.setClosedAt(Instant.now());
        stocktakeRepository.save(st);
        publish(st, "StocktakeClosed", Map.of("items", items.size()));
        return Map.of("stocktakeNo", stocktakeNo, "status", st.getStatus().name());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detail(String stocktakeNo) {
        Stocktake st = getStocktake(stocktakeNo);
        List<Map<String, Object>> lines = itemRepository.findByStocktakeId(st.getId()).stream()
                .map(this::view).toList();
        Machine m = machineRepository.findById(st.getMachineId()).orElse(null);
        return Map.of("stocktakeNo", st.getStocktakeNo(),
                "machineCode", m == null ? null : m.getCode(),
                "status", st.getStatus().name(), "note", st.getNote() == null ? "" : st.getNote(),
                "items", lines);
    }

    private void ensureOpen(Stocktake st) {
        if (st.getStatus() != StocktakeStatus.OPEN) {
            throw new IllegalStateException("盘点单已关闭: " + st.getStocktakeNo());
        }
    }

    private Map<String, Object> view(StocktakeItem si) {
        Slot slot = slotRepository.findById(si.getSlotId()).orElse(null);
        Batch b = si.getBatchId() == null ? null : batchRepository.findById(si.getBatchId()).orElse(null);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("itemId", si.getId());
        m.put("slotCode", slot == null ? null : slot.getCode());
        m.put("batchNo", b == null ? null : b.getBatchNo());
        m.put("systemQty", si.getSystemQty());
        m.put("actualQty", si.getActualQty());
        m.put("diffQty", si.getDiffQty());
        m.put("state", si.getState().name());
        m.put("resolution", si.getResolution() == null ? null : si.getResolution().name());
        return m;
    }

    private void publish(Stocktake st, String type, Map<String, Object> extra) {
        Machine m = machineRepository.findById(st.getMachineId()).orElse(null);
        Map<String, Object> payload = new LinkedHashMap<>(extra);
        payload.put("stocktakeNo", st.getStocktakeNo());
        payload.put("machineCode", m == null ? "" : m.getCode());
        payload.put("occurredAt", Instant.now().toString());
        eventPublisher.publish(new DomainEvent(Topics.STOCKTAKE,
                m == null ? st.getStocktakeNo() : m.getCode(), type, json(payload)));
    }

    private Stocktake getStocktake(String no) {
        return stocktakeRepository.findAll().stream().filter(s -> s.getStocktakeNo().equals(no)).findFirst()
                .orElseThrow(() -> new EntityNotFoundException("盘点单不存在: " + no));
    }

    private Machine findMachine(String code) {
        return machineRepository.findAll().stream().filter(m -> m.getCode().equals(code)).findFirst()
                .orElseThrow(() -> new EntityNotFoundException("货柜不存在: " + code));
    }

    private Batch findBatch(String no) {
        return batchRepository.findAll().stream().filter(b -> b.getBatchNo().equals(no)).findFirst()
                .orElseThrow(() -> new EntityNotFoundException("批次不存在: " + no));
    }

    private String json(Object o) {
        try { return mapper.writeValueAsString(o); }
        catch (JsonProcessingException e) { return "{}"; }
    }
}
