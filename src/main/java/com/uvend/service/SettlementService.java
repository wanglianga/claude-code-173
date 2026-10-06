package com.uvend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uvend.domain.entity.*;
import com.uvend.domain.enums.Bearer;
import com.uvend.domain.enums.OrderStatus;
import com.uvend.domain.enums.SettlementStatus;
import com.uvend.domain.repo.*;
import com.uvend.event.DomainEvent;
import com.uvend.event.EventPublisher;
import com.uvend.event.Topics;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

/**
 * 财务结算（按真实售卖与报损结算供应商）：
 * - 真实售卖：取周期内已确认/退款订单明细，销量扣除已退数量，金额按比例冲减
 * - 报损：仅供应商承担（bearer=SUPPLIER）的报损按成本从应付款扣除；运营自担部分只展示不扣
 * - 按批次明细（settlement_item），支持供应商按批次核对
 */
@Service
public class SettlementService {

    private final SettlementRepository settlementRepository;
    private final SettlementItemRepository itemRepository;
    private final SupplierRepository supplierRepository;
    private final BatchRepository batchRepository;
    private final ProductRepository productRepository;
    private final OrderInfoRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final LossRecordRepository lossRepository;
    private final RefundRepository refundRepository;
    private final EventPublisher eventPublisher;
    private final Numbers numbers;
    private final ObjectMapper mapper;

    public SettlementService(SettlementRepository settlementRepository, SettlementItemRepository itemRepository,
                             SupplierRepository supplierRepository, BatchRepository batchRepository,
                             ProductRepository productRepository, OrderInfoRepository orderRepository,
                             OrderItemRepository orderItemRepository,
                             LossRecordRepository lossRepository, RefundRepository refundRepository,
                             EventPublisher eventPublisher, Numbers numbers, ObjectMapper mapper) {
        this.settlementRepository = settlementRepository;
        this.itemRepository = itemRepository;
        this.supplierRepository = supplierRepository;
        this.batchRepository = batchRepository;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.lossRepository = lossRepository;
        this.refundRepository = refundRepository;
        this.eventPublisher = eventPublisher;
        this.numbers = numbers;
        this.mapper = mapper;
    }

    @Transactional
    public Map<String, Object> generate(Long supplierId, LocalDate periodStart, LocalDate periodEnd) {
        Supplier supplier = supplierRepository.findById(supplierId)
                .orElseThrow(() -> new EntityNotFoundException("供应商不存在: " + supplierId));
        if (periodEnd.isBefore(periodStart)) throw new IllegalArgumentException("结算周期结束早于开始");
        if (settlementRepository.existsBySupplierIdAndPeriodStartAndPeriodEnd(supplierId, periodStart, periodEnd)) {
            throw new IllegalStateException("该供应商该周期结算单已存在，请勿重复生成");
        }
        Instant from = periodStart.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to = periodEnd.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        // batch -> [soldQty, salesAmount, soldCost]
        Map<Long, long[]> qtyHolder = new HashMap<>();
        Map<Long, BigDecimal[]> amtHolder = new HashMap<>();

        List<OrderStatus> settledStatuses = List.of(OrderStatus.CONFIRMED, OrderStatus.REFUNDED,
                OrderStatus.PARTIALLY_REFUNDED);
        for (OrderInfo order : orderRepository.findInPeriodWithStatus(from, to, settledStatuses)) {
            for (OrderItem oi : orderItemRepository.findByOrderId(order.getId())) {
                Batch batch = oi.getBatchId() == null ? null : batchRepository.findById(oi.getBatchId()).orElse(null);
                if (batch == null || !batch.getSupplierId().equals(supplierId)) continue;
                int effectiveQty = oi.getQty() - oi.getRefundedQty();
                if (effectiveQty <= 0) continue;
                BigDecimal effectiveAmount = oi.getQty() == 0 ? BigDecimal.ZERO :
                        oi.getAmount().multiply(BigDecimal.valueOf(effectiveQty))
                                .divide(BigDecimal.valueOf(oi.getQty()), 2, RoundingMode.HALF_UP);
                Product p = productRepository.findById(batch.getProductId()).orElseThrow();
                BigDecimal cost = p.getCostPrice().multiply(BigDecimal.valueOf(effectiveQty));

                qtyHolder.computeIfAbsent(batch.getId(), k -> new long[1])[0] += effectiveQty;
                BigDecimal[] a = amtHolder.computeIfAbsent(batch.getId(),
                        k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                a[0] = a[0].add(effectiveAmount);
                a[1] = a[1].add(cost);
            }
        }

        // batch -> [lossQtySupplier, lossCostSupplier, lossQtyOperator, lossCostOperator]
        Map<Long, BigDecimal[]> lossAmt = new HashMap<>();
        Map<Long, long[]> lossQty = new HashMap<>();
        for (LossRecord lr : lossRepository.findByCreatedAtBetween(from, to)) {
            Batch batch = batchRepository.findById(lr.getBatchId()).orElse(null);
            if (batch == null || !batch.getSupplierId().equals(supplierId)) continue;
            boolean borne = lr.getBearer() == Bearer.SUPPLIER;
            long[] q = lossQty.computeIfAbsent(batch.getId(), k -> new long[2]);
            BigDecimal[] a = lossAmt.computeIfAbsent(batch.getId(),
                    k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            if (borne) { q[0] += lr.getQty(); a[0] = a[0].add(lr.getCostAmount()); }
            else { q[1] += lr.getQty(); a[1] = a[1].add(lr.getCostAmount()); }
        }

        Settlement st = new Settlement();
        st.setSettlementNo(numbers.next("STL"));
        st.setSupplierId(supplierId);
        st.setPeriodStart(periodStart);
        st.setPeriodEnd(periodEnd);
        st.setStatus(SettlementStatus.DRAFT);
        settlementRepository.save(st);

        Set<Long> batchIds = new HashSet<>();
        batchIds.addAll(qtyHolder.keySet());
        batchIds.addAll(lossQty.keySet());

        BigDecimal totalSales = BigDecimal.ZERO, totalLoss = BigDecimal.ZERO, totalPayable = BigDecimal.ZERO;
        List<Map<String, Object>> lines = new ArrayList<>();
        for (Long batchId : batchIds) {
            Batch batch = batchRepository.findById(batchId).orElseThrow();
            long soldQty = qtyHolder.getOrDefault(batchId, new long[1])[0];
            BigDecimal[] sales = amtHolder.getOrDefault(batchId,
                    new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            long[] lq = lossQty.getOrDefault(batchId, new long[2]);
            BigDecimal[] la = lossAmt.getOrDefault(batchId,
                    new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            BigDecimal payable = sales[1].subtract(la[0]); // 供货成本 - 供应商承担损耗

            SettlementItem si = new SettlementItem();
            si.setSettlementId(st.getId());
            si.setBatchId(batchId);
            si.setSoldQty((int) soldQty);
            si.setSalesAmount(sales[0]);
            si.setLossQty((int) lq[0]);
            si.setLossAmount(la[0]);
            si.setPayableAmount(payable);
            itemRepository.save(si);

            totalSales = totalSales.add(sales[0]);
            totalLoss = totalLoss.add(la[0]);
            totalPayable = totalPayable.add(payable);
            lines.add(Map.of("batchNo", batch.getBatchNo(), "soldQty", soldQty,
                    "salesAmount", sales[0], "supplierLossQty", lq[0], "supplierLossAmount", la[0],
                    "operatorLossAmount", la[1], "payableAmount", payable));
        }

        st.setSalesAmount(totalSales);
        st.setLossAmount(totalLoss);
        st.setPayableAmount(totalPayable);
        settlementRepository.save(st);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("settlementNo", st.getSettlementNo());
        payload.put("supplierCode", supplier.getCode());
        payload.put("period", periodStart + "~" + periodEnd);
        payload.put("salesAmount", totalSales);
        payload.put("lossAmount", totalLoss);
        payload.put("payableAmount", totalPayable);
        payload.put("batches", lines.size());
        payload.put("occurredAt", Instant.now().toString());
        eventPublisher.publish(new DomainEvent(Topics.SETTLEMENT, supplier.getCode(),
                "SettlementGenerated", json(payload)));

        return Map.of("settlementNo", st.getSettlementNo(), "supplierId", supplierId,
                "supplierName", supplier.getName(), "period", periodStart + "~" + periodEnd,
                "status", st.getStatus().name(), "salesAmount", totalSales,
                "supplierBorneLoss", totalLoss, "payableAmount", totalPayable, "items", lines);
    }

    @Transactional
    public Map<String, Object> confirm(String settlementNo) {
        Settlement st = get(settlementNo);
        if (st.getStatus() != SettlementStatus.DRAFT) {
            throw new IllegalStateException("仅草稿可确认");
        }
        st.setStatus(SettlementStatus.CONFIRMED);
        st.setConfirmedAt(Instant.now());
        settlementRepository.save(st);
        return Map.of("settlementNo", settlementNo, "status", st.getStatus().name(),
                "payableAmount", st.getPayableAmount());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detail(String settlementNo) {
        Settlement st = get(settlementNo);
        Supplier s = supplierRepository.findById(st.getSupplierId()).orElse(null);
        List<Map<String, Object>> items = itemRepository.findBySettlementId(st.getId()).stream().map(si -> {
            Batch b = batchRepository.findById(si.getBatchId()).orElse(null);
            Product p = b == null ? null : productRepository.findById(b.getProductId()).orElse(null);
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("batchNo", b == null ? null : b.getBatchNo());
            m.put("product", p == null ? null : p.getName());
            m.put("soldQty", si.getSoldQty());
            m.put("salesAmount", si.getSalesAmount());
            m.put("lossQty", si.getLossQty());
            m.put("lossAmount", si.getLossAmount());
            m.put("payableAmount", si.getPayableAmount());
            return m;
        }).toList();
        return Map.of("settlementNo", st.getSettlementNo(),
                "supplierName", s == null ? null : s.getName(),
                "period", st.getPeriodStart() + "~" + st.getPeriodEnd(),
                "status", st.getStatus().name(),
                "salesAmount", st.getSalesAmount(), "lossAmount", st.getLossAmount(),
                "payableAmount", st.getPayableAmount(), "items", items);
    }

    private Settlement get(String no) {
        return settlementRepository.findAll().stream()
                .filter(s -> s.getSettlementNo().equals(no)).findFirst()
                .orElseThrow(() -> new EntityNotFoundException("结算单不存在: " + no));
    }

    private String json(Object o) {
        try { return mapper.writeValueAsString(o); }
        catch (JsonProcessingException e) { return "{}"; }
    }
}
