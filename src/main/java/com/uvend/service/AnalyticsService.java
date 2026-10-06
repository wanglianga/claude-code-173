package com.uvend.service;

import com.uvend.domain.entity.*;
import com.uvend.domain.enums.LedgerReason;
import com.uvend.domain.enums.LossReason;
import com.uvend.domain.enums.OffShelfReason;
import com.uvend.domain.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/**
 * 运营分析：按点位（社区/办公楼/医院）统计销量、损耗、临期率，支持补货与品类决策。
 * 临期率 = 因临期/过期产生的下架+报损数量 / 补货入库数量。
 */
@Service
public class AnalyticsService {

    private final MachineRepository machineRepository;
    private final InventoryLedgerRepository ledgerRepository;
    private final LossRecordRepository lossRepository;
    private final OffShelfTaskRepository offShelfRepository;
    private final OrderInfoRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final BatchRepository batchRepository;
    private final ProductRepository productRepository;

    public AnalyticsService(MachineRepository machineRepository, InventoryLedgerRepository ledgerRepository,
                            LossRecordRepository lossRepository, OffShelfTaskRepository offShelfRepository,
                            OrderInfoRepository orderRepository, OrderItemRepository orderItemRepository,
                            BatchRepository batchRepository, ProductRepository productRepository) {
        this.machineRepository = machineRepository;
        this.ledgerRepository = ledgerRepository;
        this.lossRepository = lossRepository;
        this.offShelfRepository = offShelfRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.batchRepository = batchRepository;
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> machineReport(Long machineId) {
        Machine machine = machineRepository.findById(machineId)
                .orElseThrow(() -> new java.util.NoSuchElementException("货柜不存在: " + machineId));

        int restocked = 0, soldQty = 0, lossQty = 0, nearExpiryQty = 0;
        for (InventoryLedger l : ledgerRepository.findByMachineIdOrderByIdAsc(machineId)) {
            if (l.getReason() == LedgerReason.RESTOCK) restocked += l.getChangeQty();
            if (l.getReason() == LedgerReason.SALE) soldQty += -l.getChangeQty();
            if (l.getReason() == LedgerReason.LOSS) lossQty += -l.getChangeQty();
        }
        for (OffShelfTask t : offShelfRepository.findByMachineId(machineId)) {
            if (t.getReason() == OffShelfReason.NEAR_EXPIRY || t.getReason() == OffShelfReason.EXPIRED) {
                nearExpiryQty += t.getQty();
            }
        }
        BigDecimal lossCost = lossRepository.findByMachineId(machineId).stream()
                .map(LossRecord::getCostAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal salesAmount = orderRepository.findByMachineId(machineId).stream()
                .filter(o -> o.getStatus().name().contains("REFUND") || o.getStatus().name().equals("CONFIRMED"))
                .map(OrderInfo::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        // 商品销量排行
        Map<Long, int[]> productQty = new LinkedHashMap<>();
        for (OrderInfo o : orderRepository.findByMachineId(machineId)) {
            for (OrderItem oi : orderItemRepository.findByOrderId(o.getId())) {
                productQty.computeIfAbsent(oi.getProductId(), k -> new int[1])[0]
                        += oi.getQty() - oi.getRefundedQty();
            }
        }
        List<Map<String, Object>> ranking = new ArrayList<>();
        productQty.entrySet().stream().sorted((a, b) -> b.getValue()[0] - a.getValue()[0])
                .forEach(e -> {
                    Product p = productRepository.findById(e.getKey()).orElse(null);
                    ranking.add(Map.of("product", p == null ? String.valueOf(e.getKey()) : p.getName(),
                            "qty", Math.max(0, e.getValue()[0])));
                });

        double nearRate = restocked == 0 ? 0.0
                : Math.round(nearExpiryQty * 10000.0 / restocked) / 100.0;
        double lossRate = restocked == 0 ? 0.0
                : Math.round(lossQty * 10000.0 / restocked) / 100.0;

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("machineCode", machine.getCode());
        report.put("machineName", machine.getName());
        report.put("locationType", machine.getLocationType().name());
        report.put("address", machine.getAddress() == null ? "" : machine.getAddress());
        report.put("restockedQty", restocked);
        report.put("soldQty", soldQty);
        report.put("salesAmount", salesAmount);
        report.put("lossQty", lossQty);
        report.put("lossCost", lossCost);
        report.put("nearExpiryQty", nearExpiryQty);
        report.put("nearExpiryRatePct", nearRate);
        report.put("lossRatePct", lossRate);
        report.put("productRanking", ranking);
        return report;
    }

    /** 按点位类型汇总（补货计划看板）。 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> locationSummary() {
        Map<String, int[]> aggQty = new TreeMap<>();   // [restock, sold, loss, near]
        Map<String, BigDecimal> aggSales = new TreeMap<>();
        for (Machine m : machineRepository.findAll()) {
            Map<String, Object> r = machineReport(m.getId());
            String type = m.getLocationType().name();
            int[] q = aggQty.computeIfAbsent(type, k -> new int[4]);
            q[0] += (int) r.get("restockedQty");
            q[1] += (int) r.get("soldQty");
            q[2] += (int) r.get("lossQty");
            q[3] += (int) r.get("nearExpiryQty");
            aggSales.merge(type, (BigDecimal) r.get("salesAmount"), BigDecimal::add);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (var e : aggQty.entrySet()) {
            int[] q = e.getValue();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("locationType", e.getKey());
            row.put("restockedQty", q[0]);
            row.put("soldQty", q[1]);
            row.put("lossQty", q[2]);
            row.put("nearExpiryQty", q[3]);
            row.put("salesAmount", aggSales.getOrDefault(e.getKey(), BigDecimal.ZERO));
            row.put("nearExpiryRatePct", q[0] == 0 ? 0.0 : Math.round(q[3] * 10000.0 / q[0]) / 100.0);
            row.put("lossRatePct", q[0] == 0 ? 0.0 : Math.round(q[2] * 10000.0 / q[0]) / 100.0);
            out.add(row);
        }
        return out;
    }
}
