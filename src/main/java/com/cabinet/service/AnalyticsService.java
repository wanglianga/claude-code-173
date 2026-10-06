package com.cabinet.service;

import com.cabinet.domain.*;
import com.cabinet.repo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 运营统计：按点位汇总销量、销售额、损耗（数量/成本）与临期率，
 * 支撑按点位调整品类与补货计划；供应商/运营可按批次查看损耗原因与全链路台账。
 */
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private static final int NEAR_EXPIRY_DAYS = 2;

    private final CabinetRepository cabinetRepository;
    private final LossRecordRepository lossRepository;
    private final ChannelStockRepository stockRepository;
    private final ProductBatchRepository batchRepository;
    private final BatchLedgerRepository ledgerRepository;
    private final OrderRecordRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    public record PointStat(String pointCode, String pointName, String type,
                            long cabinetCount, int soldQty, BigDecimal soldAmount,
                            int lossQty, BigDecimal lossCost,
                            int nearExpiryQty, int onHandQty,
                            BigDecimal lossRate, BigDecimal nearExpiryRate) {}

    @Transactional(readOnly = true)
    public List<PointStat> pointStats(LocalDate from, LocalDate to) {
        LocalDateTime fromT = from.atStartOfDay();
        LocalDateTime toT = to.plusDays(1).atStartOfDay();
        LocalDate nearLine = LocalDate.now().plusDays(NEAR_EXPIRY_DAYS);

        Map<Long, Point> pointMap = new LinkedHashMap<>();
        Map<Long, List<Cabinet>> cabinetsByPoint = new LinkedHashMap<>();
        for (Cabinet c : cabinetRepository.findAll()) {
            if (c.getPoint() == null) continue;
            pointMap.put(c.getPoint().getId(), c.getPoint());
            cabinetsByPoint.computeIfAbsent(c.getPoint().getId(), k -> new ArrayList<>()).add(c);
        }

        List<PointStat> result = new ArrayList<>();
        for (var entry : cabinetsByPoint.entrySet()) {
            Point p = pointMap.get(entry.getKey());
            List<Cabinet> cabinets = entry.getValue();
            List<Long> cabinetIds = cabinets.stream().map(Cabinet::getId).toList();

            int soldQty = 0;
            BigDecimal soldAmount = BigDecimal.ZERO;
            for (OrderRecord order : orderRepository.findByCabinetIdIn(cabinetIds)) {
                LocalDateTime t = order.getChargedAt();
                if (t == null || t.isBefore(fromT) || !t.isBefore(toT)) continue;
                List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());
                for (OrderItem oi : items) {
                    soldQty += oi.getQty() - oi.getRefundedQty();
                }
                // 净销售额 = 单额 - 已退
                soldAmount = soldAmount.add(order.getTotalAmount())
                        .subtract(order.getRefundedAmount());
            }

            int lossQty = 0;
            BigDecimal lossCost = BigDecimal.ZERO;
            for (Long cid : cabinetIds) {
                for (LossRecord lr : lossRepository.findByCabinetId(cid)) {
                    LocalDateTime t = lr.getCreatedAt();
                    if (t.isBefore(fromT) || !t.isBefore(toT)) continue;
                    lossQty += lr.getQty();
                    lossCost = lossCost.add(lr.getCostAmount() != null
                            ? lr.getCostAmount() : BigDecimal.ZERO);
                }
            }

            int onHand = 0;
            int nearExpiry = 0;
            for (Long cid : cabinetIds) {
                for (ChannelStock s : stockRepository.findByCabinetId(cid)) {
                    onHand += s.getQtyOnHand();
                    LocalDate exp = s.getBatch().getExpiryDate();
                    if (s.getQtyOnHand() > 0 && exp != null && !exp.isAfter(nearLine)) {
                        nearExpiry += s.getQtyOnHand();
                    }
                }
            }

            int throughput = soldQty + lossQty;
            BigDecimal lossRate = throughput == 0 ? BigDecimal.ZERO
                    : BigDecimal.valueOf(lossQty * 100.0 / throughput)
                            .setScale(2, RoundingMode.HALF_UP);
            BigDecimal nearRate = onHand == 0 ? BigDecimal.ZERO
                    : BigDecimal.valueOf(nearExpiry * 100.0 / onHand)
                            .setScale(2, RoundingMode.HALF_UP);

            result.add(new PointStat(p.getCode(), p.getName(), p.getType().name(),
                    cabinets.size(), soldQty, soldAmount, lossQty, lossCost,
                    nearExpiry, onHand, lossRate, nearRate));
        }
        return result;
    }

    /** 批次损耗明细（供应商/运营视角，含原因与责任归属） */
    @Transactional(readOnly = true)
    public List<LossRecord> batchLosses(String batchNo) {
        ProductBatch batch = batchRepository.findByBatchNo(batchNo)
                .orElseThrow(() -> new IllegalArgumentException("批次不存在: " + batchNo));
        return lossRepository.findByBatchIdOrderByIdAsc(batch.getId());
    }

    /** 批次全链路台账：补货/售卖/下架/报损/盘点/退款/结算 */
    @Transactional(readOnly = true)
    public List<BatchLedger> batchTrace(String batchNo) {
        ProductBatch batch = batchRepository.findByBatchNo(batchNo)
                .orElseThrow(() -> new IllegalArgumentException("批次不存在: " + batchNo));
        return ledgerRepository.findByBatchIdOrderByIdAsc(batch.getId());
    }
}
