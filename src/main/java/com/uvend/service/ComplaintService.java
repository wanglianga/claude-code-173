package com.uvend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uvend.domain.entity.*;
import com.uvend.domain.enums.*;
import com.uvend.domain.repo.*;
import com.uvend.event.DomainEvent;
import com.uvend.event.EventPublisher;
import com.uvend.event.Topics;
import com.uvend.web.dto.ComplaintDecisionRequest;
import com.uvend.web.dto.ComplaintRequest;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * 投诉与售后：
 * - 用户投诉（误扣款/未拿取/质量）
 * - 客服证据回放：开门记录、原始事件流、识别结果、视频片段、订单明细、会话前后库存变化
 * - 退款（全额/部分/按明细）驱动订单状态与退款台账，发布事件
 */
@Service
public class ComplaintService {

    private final ComplaintRepository complaintRepository;
    private final RefundRepository refundRepository;
    private final OrderInfoRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final DoorSessionRepository sessionRepository;
    private final EventLogRepository eventLogRepository;
    private final RecognitionResultRepository recognitionRepository;
    private final InventoryLedgerRepository ledgerRepository;
    private final MachineRepository machineRepository;
    private final BatchRepository batchRepository;
    private final ProductRepository productRepository;
    private final SlotRepository slotRepository;
    private final EventPublisher eventPublisher;
    private final Numbers numbers;
    private final ObjectMapper mapper;

    public ComplaintService(ComplaintRepository complaintRepository, RefundRepository refundRepository,
                            OrderInfoRepository orderRepository, OrderItemRepository orderItemRepository,
                            DoorSessionRepository sessionRepository, EventLogRepository eventLogRepository,
                            RecognitionResultRepository recognitionRepository,
                            InventoryLedgerRepository ledgerRepository, MachineRepository machineRepository,
                            BatchRepository batchRepository, ProductRepository productRepository,
                            SlotRepository slotRepository, EventPublisher eventPublisher,
                            Numbers numbers, ObjectMapper mapper) {
        this.complaintRepository = complaintRepository;
        this.refundRepository = refundRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.sessionRepository = sessionRepository;
        this.eventLogRepository = eventLogRepository;
        this.recognitionRepository = recognitionRepository;
        this.ledgerRepository = ledgerRepository;
        this.machineRepository = machineRepository;
        this.batchRepository = batchRepository;
        this.productRepository = productRepository;
        this.slotRepository = slotRepository;
        this.eventPublisher = eventPublisher;
        this.numbers = numbers;
        this.mapper = mapper;
    }

    @Transactional
    public Map<String, Object> create(Long userId, ComplaintRequest req) {
        OrderInfo order = findOrder(req.orderNo());
        if (!order.getUserId().equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException("只能投诉本人订单");
        }
        Complaint c = new Complaint();
        c.setComplaintNo(numbers.next("CP"));
        c.setUserId(userId);
        c.setOrderId(order.getId());
        c.setType(ComplaintType.valueOf(req.type()));
        c.setDescription(req.description());
        c.setStatus(ComplaintStatus.OPEN);
        complaintRepository.save(c);

        order.setStatus(OrderStatus.DISPUTED);
        orderRepository.save(order);
        DoorSession s = sessionRepository.findById(order.getSessionId()).orElse(null);
        if (s != null) {
            s.setStatus(SessionStatus.DISPUTED);
            sessionRepository.save(s);
        }
        publish("ComplaintCreated", c.getComplaintNo(), order, Map.of(
                "type", c.getType().name(), "description", req.description() == null ? "" : req.description()));
        return Map.of("complaintNo", c.getComplaintNo(), "status", c.getStatus().name(),
                "orderNo", order.getOrderNo());
    }

    /** 客服证据回放包：短时间内开门/拿放/识别/库存变化的完整可回放数据。 */
    @Transactional(readOnly = true)
    public Map<String, Object> evidenceBundle(String complaintNo) {
        Complaint c = findComplaint(complaintNo);
        OrderInfo order = orderRepository.findById(c.getOrderId()).orElseThrow();
        DoorSession session = sessionRepository.findById(order.getSessionId()).orElseThrow();
        Machine machine = machineRepository.findById(order.getMachineId()).orElse(null);

        List<Map<String, Object>> events = new ArrayList<>();
        for (EventLog e : eventLogRepository.findBySessionIdOrderBySeqAsc(session.getId())) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("seq", e.getSeq());
            m.put("type", e.getEventType().name());
            m.put("method", e.getMethod() == null ? null : e.getMethod().name());
            m.put("confidence", e.getConfidence());
            m.put("payload", parse(e.getPayload()));
            m.put("occurredAt", e.getOccurredAt());
            events.add(m);
        }
        List<Map<String, Object>> recognitions = recognitionRepository.findBySessionId(session.getId())
                .stream().map(r -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    Batch b = r.getBatchId() == null ? null : batchRepository.findById(r.getBatchId()).orElse(null);
                    Product p = r.getProductId() == null ? null : productRepository.findById(r.getProductId()).orElse(null);
                    m.put("method", r.getMethod().name());
                    m.put("product", p == null ? null : p.getName());
                    m.put("batchNo", b == null ? null : b.getBatchNo());
                    m.put("confidence", r.getConfidence());
                    m.put("verdict", r.getVerdict().name());
                    return m;
                }).toList();

        List<Map<String, Object>> orderItems = orderItemRepository.findByOrderId(order.getId()).stream()
                .map(oi -> {
                    Product p = productRepository.findById(oi.getProductId()).orElse(null);
                    Batch b = oi.getBatchId() == null ? null : batchRepository.findById(oi.getBatchId()).orElse(null);
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("orderItemId", oi.getId());
                    m.put("product", p == null ? null : p.getName());
                    m.put("batchNo", b == null ? null : b.getBatchNo());
                    m.put("qty", oi.getQty());
                    m.put("refundedQty", oi.getRefundedQty());
                    m.put("amount", oi.getAmount());
                    m.put("source", oi.getSource().name());
                    return m;
                }).toList();

        // 会话前后 ±2 分钟该柜库存变化
        Instant from = session.getOpenedAt().minusSeconds(120);
        Instant to = (session.getClosedAt() == null ? Instant.now() : session.getClosedAt()).plusSeconds(120);
        List<Map<String, Object>> stockChanges = ledgerRepository
                .findByMachineIdAndCreatedAtBetween(order.getMachineId(), from, to).stream()
                .map(l -> {
                    Slot slot = slotRepository.findById(l.getSlotId()).orElse(null);
                    Batch b = batchRepository.findById(l.getBatchId()).orElse(null);
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("at", l.getCreatedAt());
                    m.put("slotCode", slot == null ? null : slot.getCode());
                    m.put("batchNo", b == null ? null : b.getBatchNo());
                    m.put("change", l.getChangeQty());
                    m.put("balance", l.getBalanceQty());
                    m.put("reason", l.getReason().name());
                    m.put("ref", (l.getRefType() == null ? "" : l.getRefType()) + "#" + (l.getRefId() == null ? "" : l.getRefId()));
                    return m;
                }).toList();

        Map<String, Object> bundle = new LinkedHashMap<>();
        bundle.put("complaintNo", c.getComplaintNo());
        bundle.put("type", c.getType().name());
        bundle.put("description", c.getDescription());
        bundle.put("complaintStatus", c.getStatus().name());
        bundle.put("orderNo", order.getOrderNo());
        bundle.put("orderStatus", order.getStatus().name());
        bundle.put("evidence", order.getEvidence().name());
        bundle.put("totalAmount", order.getTotalAmount());
        bundle.put("machineCode", machine == null ? null : machine.getCode());
        bundle.put("machineName", machine == null ? null : machine.getName());
        bundle.put("sessionNo", session.getSessionNo());
        bundle.put("openedAt", session.getOpenedAt());
        bundle.put("closedAt", session.getClosedAt());
        bundle.put("videoClipUrl", session.getVideoClipUrl());
        bundle.put("eventTimeline", events);
        bundle.put("recognitions", recognitions);
        bundle.put("orderItems", orderItems);
        bundle.put("stockChanges", stockChanges);
        return bundle;
    }

    @Transactional
    public Map<String, Object> decide(String complaintNo, Long handlerId, ComplaintDecisionRequest req) {
        Complaint c = findComplaint(complaintNo);
        OrderInfo order = orderRepository.findById(c.getOrderId()).orElseThrow();
        c.setHandlerId(handlerId);
        c.setResolution(req.resolution());

        if ("REJECT".equalsIgnoreCase(req.decision())) {
            c.setStatus(ComplaintStatus.REJECTED);
            c.setResolvedAt(Instant.now());
            complaintRepository.save(c);
            if (order.getStatus() == OrderStatus.DISPUTED) {
                order.setStatus(OrderStatus.CONFIRMED);
                orderRepository.save(order);
            }
            publish("ComplaintRejected", complaintNo, order, Map.of("resolution", nz(req.resolution())));
            return Map.of("complaintNo", complaintNo, "status", c.getStatus().name());
        }
        if (!"REFUND".equalsIgnoreCase(req.decision())) {
            throw new IllegalArgumentException("decision 必须为 REFUND 或 REJECT");
        }

        // 退款金额：指定明细金额优先，其次手工金额，最后全额
        java.math.BigDecimal amount = java.math.BigDecimal.ZERO;
        if (req.refundItemIds() != null && !req.refundItemIds().isEmpty()) {
            for (Long itemId : req.refundItemIds()) {
                OrderItem oi = orderItemRepository.findById(itemId)
                        .orElseThrow(() -> new EntityNotFoundException("订单项不存在: " + itemId));
                if (!oi.getOrderId().equals(order.getId())) {
                    throw new IllegalArgumentException("订单项不属于该订单: " + itemId);
                }
                int remain = oi.getQty() - oi.getRefundedQty();
                if (remain <= 0) throw new IllegalStateException("明细已全额退款: " + itemId);
                oi.setRefundedQty(oi.getQty());
                orderItemRepository.save(oi);
                amount = amount.add(oi.getAmount());
            }
        } else if (req.refundAmount() != null) {
            amount = req.refundAmount();
            if (amount.signum() < 0 || amount.compareTo(order.getTotalAmount()) > 0) {
                throw new IllegalArgumentException("退款金额超出订单金额");
            }
        } else {
            amount = order.getTotalAmount();
            orderItemRepository.findByOrderId(order.getId()).forEach(oi -> {
                oi.setRefundedQty(oi.getQty());
                orderItemRepository.save(oi);
            });
        }
        if (amount.signum() <= 0) throw new IllegalArgumentException("退款金额必须大于 0");

        Refund refund = new Refund();
        refund.setRefundNo(numbers.next("RF"));
        refund.setComplaintId(c.getId());
        refund.setOrderId(order.getId());
        refund.setAmount(amount);
        refund.setReason(req.resolution());
        refundRepository.save(refund);

        boolean full = amount.compareTo(order.getTotalAmount()) >= 0;
        order.setStatus(full ? OrderStatus.REFUNDED : OrderStatus.PARTIALLY_REFUNDED);
        order.setSettledAt(Instant.now());
        orderRepository.save(order);

        c.setStatus(ComplaintStatus.REFUNDED);
        c.setResolvedAt(Instant.now());
        complaintRepository.save(c);

        publish("ComplaintRefunded", complaintNo, order, Map.of(
                "refundNo", refund.getRefundNo(), "amount", amount, "full", full));
        return Map.of("complaintNo", complaintNo, "status", c.getStatus().name(),
                "refundNo", refund.getRefundNo(), "amount", amount, "full", full);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(String status) {
        return complaintRepository.findAll().stream()
                .filter(c -> status == null || status.isBlank() || c.getStatus().name().equals(status))
                .map(c -> {
                    OrderInfo o = orderRepository.findById(c.getOrderId()).orElse(null);
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("complaintNo", c.getComplaintNo());
                    m.put("orderNo", o == null ? null : o.getOrderNo());
                    m.put("type", c.getType().name());
                    m.put("status", c.getStatus().name());
                    m.put("createdAt", c.getCreatedAt());
                    return m;
                }).toList();
    }

    private Complaint findComplaint(String no) {
        return complaintRepository.findAll().stream().filter(c -> c.getComplaintNo().equals(no)).findFirst()
                .orElseThrow(() -> new EntityNotFoundException("投诉不存在: " + no));
    }

    private OrderInfo findOrder(String no) {
        return orderRepository.findAll().stream().filter(o -> o.getOrderNo().equals(no)).findFirst()
                .orElseThrow(() -> new EntityNotFoundException("订单不存在: " + no));
    }

    private void publish(String type, String complaintNo, OrderInfo order, Map<String, Object> extra) {
        Map<String, Object> payload = new LinkedHashMap<>(extra);
        payload.put("complaintNo", complaintNo);
        payload.put("orderNo", order.getOrderNo());
        payload.put("occurredAt", Instant.now().toString());
        eventPublisher.publish(new DomainEvent(Topics.COMPLAINT, complaintNo, type, json(payload)));
    }

    private String nz(String s) { return s == null ? "" : s; }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parse(String s) {
        try { return mapper.readValue(s, Map.class); }
        catch (JsonProcessingException e) { return new LinkedHashMap<>(); }
    }

    private String json(Object o) {
        try { return mapper.writeValueAsString(o); }
        catch (JsonProcessingException e) { return "{}"; }
    }
}
