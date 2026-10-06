package com.uvend.service;

import com.uvend.domain.entity.*;
import com.uvend.domain.repo.*;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/** 批次全链路追踪：补货 → 售卖 → 下架 → 报损 → 结算，供应商/运营均可按批次查看损耗原因。 */
@Service
public class BatchTraceService {

    private final BatchRepository batchRepository;
    private final ProductRepository productRepository;
    private final SupplierRepository supplierRepository;
    private final MachineRepository machineRepository;
    private final SlotRepository slotRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryLedgerRepository ledgerRepository;
    private final LossRecordRepository lossRepository;
    private final OffShelfTaskRepository offShelfRepository;
    private final QualityHoldRepository holdRepository;
    private final ReplenishmentItemRepository replenishmentItemRepository;
    private final ReplenishmentRepository replenishmentRepository;
    private final SettlementItemRepository settlementItemRepository;
    private final SettlementRepository settlementRepository;

    public BatchTraceService(BatchRepository batchRepository, ProductRepository productRepository,
                             SupplierRepository supplierRepository, MachineRepository machineRepository,
                             SlotRepository slotRepository, InventoryRepository inventoryRepository,
                             InventoryLedgerRepository ledgerRepository,
                             LossRecordRepository lossRepository, OffShelfTaskRepository offShelfRepository,
                             QualityHoldRepository holdRepository,
                             ReplenishmentItemRepository replenishmentItemRepository,
                             ReplenishmentRepository replenishmentRepository,
                             SettlementItemRepository settlementItemRepository,
                             SettlementRepository settlementRepository) {
        this.batchRepository = batchRepository;
        this.productRepository = productRepository;
        this.supplierRepository = supplierRepository;
        this.machineRepository = machineRepository;
        this.slotRepository = slotRepository;
        this.inventoryRepository = inventoryRepository;
        this.ledgerRepository = ledgerRepository;
        this.lossRepository = lossRepository;
        this.offShelfRepository = offShelfRepository;
        this.holdRepository = holdRepository;
        this.replenishmentItemRepository = replenishmentItemRepository;
        this.replenishmentRepository = replenishmentRepository;
        this.settlementItemRepository = settlementItemRepository;
        this.settlementRepository = settlementRepository;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> trace(String batchNo) {
        Batch batch = batchRepository.findAll().stream().filter(b -> b.getBatchNo().equals(batchNo)).findFirst()
                .orElseThrow(() -> new EntityNotFoundException("批次不存在: " + batchNo));
        Product product = productRepository.findById(batch.getProductId()).orElse(null);
        Supplier supplier = supplierRepository.findById(batch.getSupplierId()).orElse(null);

        List<Map<String, Object>> timeline = new ArrayList<>();
        int restocked = 0, sold = 0, offShelf = 0, loss = 0;

        for (InventoryLedger l : ledgerRepository.findByBatchIdOrderByIdAsc(batch.getId())) {
            Machine m = machineRepository.findById(l.getMachineId()).orElse(null);
            Slot slot = slotRepository.findById(l.getSlotId()).orElse(null);
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("at", l.getCreatedAt());
            e.put("machineCode", m == null ? null : m.getCode());
            e.put("slotCode", slot == null ? null : slot.getCode());
            e.put("reason", l.getReason().name());
            e.put("change", l.getChangeQty());
            e.put("balance", l.getBalanceQty());
            e.put("ref", (l.getRefType() == null ? "" : l.getRefType()) + "#" + (l.getRefId() == null ? "" : l.getRefId()));
            timeline.add(e);
            switch (l.getReason()) {
                case RESTOCK, MISPLACE_IN -> restocked += Math.max(0, l.getChangeQty());
                case SALE -> sold += -l.getChangeQty();
                case OFF_SHELF -> offShelf += -l.getChangeQty();
                case LOSS -> loss += -l.getChangeQty();
                default -> { }
            }
        }
        int onHand = inventoryRepository.findByBatchId(batch.getId()).stream()
                .mapToInt(Inventory::getQty).sum();

        List<Map<String, Object>> lossViews = new ArrayList<>();
        for (LossRecord lr : lossRepository.findByBatchId(batch.getId())) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("lossNo", lr.getLossNo());
            m.put("qty", lr.getQty());
            m.put("reason", lr.getReason().name());
            m.put("costAmount", lr.getCostAmount());
            m.put("bearer", lr.getBearer().name());
            m.put("createdAt", lr.getCreatedAt());
            lossViews.add(m);
        }

        List<Map<String, Object>> offViews = new ArrayList<>();
        for (OffShelfTask t : offShelfRepository.findByBatchId(batch.getId())) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("taskNo", t.getTaskNo());
            m.put("qty", t.getQty());
            m.put("reason", t.getReason().name());
            m.put("status", t.getStatus().name());
            m.put("createdAt", t.getCreatedAt());
            offViews.add(m);
        }

        List<Map<String, Object>> replenishViews = replenishmentItemRepository.findByBatchId(batch.getId())
                .stream().map(ri -> {
                    Replenishment r = replenishmentRepository.findById(ri.getReplenishmentId()).orElse(null);
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("state", ri.getState().name());
                    m.put("qty", ri.getQty());
                    m.put("tempRequirement", ri.getTempRequirement().name());
                    m.put("replenishNo", r == null ? null : r.getReplenishNo());
                    return m;
                }).toList();

        List<Map<String, Object>> activeHolds = holdRepository
                .findByBatchIdAndStatus(batch.getId(), com.uvend.domain.enums.HoldStatus.ACTIVE)
                .stream().map(h -> {
                    Map<String, Object> m = new LinkedHashMap<String, Object>();
                    m.put("reason", h.getReason().name());
                    m.put("machineId", h.getMachineId());
                    m.put("createdAt", h.getCreatedAt());
                    return m;
                }).toList();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("restockedQty", restocked);
        summary.put("soldQty", sold);
        summary.put("offShelfLedgerQty", offShelf);
        summary.put("lossLedgerQty", loss);
        summary.put("onHandQty", onHand);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("batchNo", batch.getBatchNo());
        result.put("product", product == null ? null : product.getName());
        result.put("supplier", supplier == null ? null : supplier.getName());
        result.put("productionDate", batch.getProductionDate().toString());
        result.put("expiryDate", batch.getExpiryDate().toString());
        result.put("inboundQty", batch.getInboundQty());
        result.put("summary", summary);
        result.put("replenishments", replenishViews);
        result.put("timeline", timeline);
        result.put("offShelfTasks", offViews);
        result.put("losses", lossViews);
        result.put("activeQualityHolds", activeHolds);
        result.put("settlements", settlementViews(batch.getId()));
        return result;
    }

    private List<Map<String, Object>> settlementViews(Long batchId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (SettlementItem si : settlementItemRepository.findAll()) {
            if (!si.getBatchId().equals(batchId)) continue;
            Settlement st = settlementRepository.findById(si.getSettlementId()).orElse(null);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("settlementNo", st == null ? null : st.getSettlementNo());
            m.put("soldQty", si.getSoldQty());
            m.put("salesAmount", si.getSalesAmount());
            m.put("lossQty", si.getLossQty());
            m.put("lossAmount", si.getLossAmount());
            m.put("payableAmount", si.getPayableAmount());
            out.add(m);
        }
        return out;
    }
}
