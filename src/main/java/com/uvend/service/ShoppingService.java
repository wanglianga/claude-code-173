package com.uvend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uvend.domain.entity.*;
import com.uvend.domain.enums.*;
import com.uvend.domain.repo.*;
import com.uvend.event.DomainEvent;
import com.uvend.event.EventPublisher;
import com.uvend.event.Topics;
import com.uvend.web.dto.EventBatchRequest;
import com.uvend.web.dto.EventRequest;
import com.uvend.web.dto.OpenSessionRequest;
import com.uvend.web.dto.ReviewRequest;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/**
 * 购物识别与关门对账（无人场景证据链核心）。
 *
 * 开门期间所有原始信号（PICK/PUT_BACK/RECOGNITION/WEIGHT/RFID/JAM/TEMPERATURE）
 * 全部落入 event_log，识别结论落 recognition_result，关门时做多模态对账：
 * - 拿起又放回：PICK 减 PUT_BACK 得净拿取，不扣款
 * - 货道卡货：该货道拿取视为未交付，不扣款
 * - 识别错误/低置信度/多模态冲突：证据不足，订单挂起转人工，不自动扣款
 * - 质量锁定批次（温控/疑似批次）：不可自动售卖
 * - 证据充分：按批次扣库存（SALE 流水）、生成订单
 */
@Service
public class ShoppingService {

    private final DoorSessionRepository sessionRepository;
    private final EventLogRepository eventLogRepository;
    private final RecognitionResultRepository recognitionRepository;
    private final OrderInfoRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final MachineRepository machineRepository;
    private final SlotRepository slotRepository;
    private final BatchRepository batchRepository;
    private final ProductRepository productRepository;
    private final QualityHoldRepository holdRepository;
    private final InventoryService inventoryService;
    private final EventPublisher eventPublisher;
    private final Numbers numbers;
    private final ObjectMapper mapper;
    private final double confidenceThreshold;

    public ShoppingService(DoorSessionRepository sessionRepository, EventLogRepository eventLogRepository,
                           RecognitionResultRepository recognitionRepository, OrderInfoRepository orderRepository,
                           OrderItemRepository orderItemRepository, MachineRepository machineRepository,
                           SlotRepository slotRepository, BatchRepository batchRepository,
                           ProductRepository productRepository, QualityHoldRepository holdRepository,
                           InventoryService inventoryService, EventPublisher eventPublisher,
                           Numbers numbers, ObjectMapper mapper,
                           @Value("${uvend.reconciliation.confidence-threshold:0.75}") double confidenceThreshold) {
        this.sessionRepository = sessionRepository;
        this.eventLogRepository = eventLogRepository;
        this.recognitionRepository = recognitionRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.machineRepository = machineRepository;
        this.slotRepository = slotRepository;
        this.batchRepository = batchRepository;
        this.productRepository = productRepository;
        this.holdRepository = holdRepository;
        this.inventoryService = inventoryService;
        this.eventPublisher = eventPublisher;
        this.numbers = numbers;
        this.mapper = mapper;
        this.confidenceThreshold = confidenceThreshold;
    }

    // ---------------- 开门 ----------------

    @Transactional
    public Map<String, Object> openSession(Long userId, OpenSessionRequest req) {
        Machine machine = findMachine(req.machineCode());
        DoorSession s = new DoorSession();
        s.setSessionNo(numbers.next("DS"));
        s.setMachineId(machine.getId());
        s.setUserId(userId);
        s.setVideoClipUrl(req.videoClipUrl());
        s.setStatus(SessionStatus.OPEN);
        sessionRepository.save(s);
        appendEvent(s, machine, EventType.DOOR_OPEN, DetectMethod.SENSOR, null, Map.of("userId", userId));

        eventPublisher.publish(new DomainEvent(Topics.SHOPPING, s.getSessionNo(), "DoorOpened",
                json(Map.of("sessionNo", s.getSessionNo(), "machineCode", machine.getCode(),
                        "userId", userId, "openedAt", Instant.now().toString()))));
        return Map.of("sessionId", s.getId(), "sessionNo", s.getSessionNo(), "status", s.getStatus().name());
    }

    // ---------------- 事件上报 ----------------

    @Transactional
    public Map<String, Object> reportEvents(Long sessionId, EventBatchRequest batch) {
        DoorSession session = getSession(sessionId);
        if (session.getStatus() != SessionStatus.OPEN) {
            throw new IllegalStateException("会话已结束，不能再上报事件: " + session.getSessionNo());
        }
        Machine machine = machineRepository.findById(session.getMachineId()).orElseThrow();
        List<Map<String, Object>> saved = new ArrayList<>();
        for (EventRequest e : batch.events()) {
            saved.add(appendFromRequest(session, machine, e));
        }
        return Map.of("sessionNo", session.getSessionNo(), "accepted", saved.size(), "events", saved);
    }

    private Map<String, Object> appendFromRequest(DoorSession session, Machine machine, EventRequest e) {
        EventType type = EventType.valueOf(e.type());
        DetectMethod method = e.method() == null || e.method().isBlank() ? null : DetectMethod.valueOf(e.method());
        Map<String, Object> payload = new LinkedHashMap<>();
        if (e.slotCode() != null) payload.put("slotCode", e.slotCode());
        if (e.batchNo() != null) payload.put("batchNo", e.batchNo());
        if (e.productSku() != null) payload.put("productSku", e.productSku());
        if (e.qty() != null) payload.put("qty", e.qty());
        if (e.detail() != null) payload.putAll(e.detail());

        EventLog log = appendEvent(session, machine, type, method,
                e.confidence() == null ? null : e.confidence().doubleValue(), payload);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("seq", log.getSeq());
        out.put("eventType", type.name());

        // 识别类事件落识别结果表（VISION/WEIGHT/RFID）
        if (type == EventType.RECOGNITION || type == EventType.WEIGHT || type == EventType.RFID) {
            DetectMethod m = type == EventType.RECOGNITION ? method :
                    DetectMethod.valueOf(type.name());
            RecognitionResult rr = buildRecognition(session, log, m, e, payload);
            out.put("verdict", rr.getVerdict().name());
            out.put("confidence", rr.getConfidence());
        }
        return out;
    }

    private RecognitionResult buildRecognition(DoorSession session, EventLog log, DetectMethod method,
                                               EventRequest e, Map<String, Object> payload) {
        BigDecimal conf = e.confidence() == null ? BigDecimal.ZERO : e.confidence();
        Long productId = null;
        Long batchId = null;
        if (e.productSku() != null) {
            Product p = productRepository.findBySku(e.productSku()).orElse(null);
            productId = p == null ? null : p.getId();
        }
        if (e.batchNo() != null) {
            Batch b = findBatchOrNull(e.batchNo());
            if (b != null) {
                batchId = b.getId();
                if (productId == null) productId = b.getProductId();
            }
        }
        Verdict verdict = conf.doubleValue() >= confidenceThreshold ? Verdict.CORRECT : Verdict.UNCERTAIN;
        RecognitionResult rr = new RecognitionResult();
        rr.setSessionId(session.getId());
        rr.setEventId(log.getId());
        rr.setMethod(method);
        rr.setProductId(productId);
        rr.setBatchId(batchId);
        rr.setConfidence(conf);
        rr.setVerdict(verdict);
        rr.setDetail(json(payload));
        return recognitionRepository.save(rr);
    }

    // ---------------- 关门对账 ----------------

    @Transactional
    public Map<String, Object> closeSession(Long sessionId, String videoClipUrl) {
        DoorSession session = getSession(sessionId);
        if (session.getStatus() == SessionStatus.CLOSED) {
            throw new IllegalStateException("会话已关闭: " + session.getSessionNo());
        }
        Machine machine = machineRepository.findById(session.getMachineId()).orElseThrow();
        if (videoClipUrl != null) session.setVideoClipUrl(videoClipUrl);

        List<EventLog> events = eventLogRepository.findBySessionIdOrderBySeqAsc(session.getId());
        appendEvent(session, machine, EventType.DOOR_CLOSE, DetectMethod.SENSOR, null, Map.of());
        session.setStatus(SessionStatus.CLOSED);
        session.setClosedAt(Instant.now());
        sessionRepository.save(session);

        Reconciliation recon = reconcile(session, machine, events);

        boolean insufficient = !recon.uncertain.isEmpty();
        boolean nothingTaken = recon.sufficient.isEmpty() && recon.uncertain.isEmpty();

        // 先对证据充分的行扣库存并计算金额
        BigDecimal total = BigDecimal.ZERO;
        List<Map<String, Object>> confirmedItems = new ArrayList<>();
        for (PickedLine line : recon.sufficient) {
            Slot slot = slotRepository.findById(line.slotId).orElseThrow();
            Batch batch = batchRepository.findById(line.batchId).orElseThrow();
            Product product = productRepository.findById(batch.getProductId()).orElseThrow();
            int deduct = Math.min(line.netQty, inventoryService.qtyOfSlotBatch(slot.getId(), batch.getId()));
            if (deduct <= 0) {
                recon.uncertain.add(line.withReason("库存为零，疑似系统库存偏差，转人工"));
                insufficient = true;
                continue;
            }
            inventoryService.apply(machine.getId(), slot.getId(), batch.getId(), -deduct,
                    LedgerReason.SALE, "ORDER", null);
            BigDecimal amount = product.getUnitPrice().multiply(BigDecimal.valueOf(deduct));
            total = total.add(amount);
            line.deductQty = deduct;
            line.amount = amount;
            confirmedItems.add(itemView(slot, batch, product, deduct, amount, line));
        }

        // 存订单
        OrderInfo order = new OrderInfo();
        order.setOrderNo(numbers.next("OD"));
        order.setSessionId(session.getId());
        order.setMachineId(machine.getId());
        order.setUserId(session.getUserId());
        order.setStatus(nothingTaken ? OrderStatus.CANCELLED
                : insufficient ? OrderStatus.PENDING : OrderStatus.CONFIRMED);
        order.setEvidence(insufficient ? EvidenceLevel.INSUFFICIENT : EvidenceLevel.SUFFICIENT);
        order.setTotalAmount(total);
        if (!insufficient) order.setSettledAt(Instant.now());
        orderRepository.save(order);

        // 再存订单项（order_id 非空）
        for (PickedLine line : recon.sufficient) {
            if (line.deductQty <= 0) continue;
            Batch batch = batchRepository.findById(line.batchId).orElseThrow();
            Product product = productRepository.findById(batch.getProductId()).orElseThrow();
            OrderItem oi = new OrderItem();
            oi.setOrderId(order.getId());
            oi.setProductId(product.getId());
            oi.setBatchId(batch.getId());
            oi.setQty(line.deductQty);
            oi.setUnitPrice(product.getUnitPrice());
            oi.setAmount(line.amount);
            orderItemRepository.save(oi);
        }

        List<Map<String, Object>> uncertainViews = new ArrayList<>();
        for (PickedLine line : recon.uncertain) {
            uncertainViews.add(lineView(line));
        }

        publishOrderEvent(order, machine, confirmedItems, uncertainViews);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderNo", order.getOrderNo());
        result.put("sessionNo", session.getSessionNo());
        result.put("status", order.getStatus().name());
        result.put("evidence", order.getEvidence().name());
        result.put("totalAmount", total);
        result.put("confirmedItems", confirmedItems);
        result.put("pendingReviewItems", uncertainViews);
        result.put("videoClipUrl", session.getVideoClipUrl());
        return result;
    }

    // ---------------- 待复核订单 ----------------

    @Transactional(readOnly = true)
    public List<Map<String, Object>> pendingOrders(String machineCode) {
        List<OrderInfo> orders = orderRepository.findAll().stream()
                .filter(o -> o.getStatus() == OrderStatus.PENDING)
                .filter(o -> machineCode == null || machineCode.isBlank()
                        || machineRepository.findById(o.getMachineId())
                                .map(Machine::getCode).stream().anyMatch(machineCode::equals))
                .toList();
        List<Map<String, Object>> out = new ArrayList<>();
        for (OrderInfo o : orders) {
            DoorSession s = sessionRepository.findById(o.getSessionId()).orElse(null);
            Machine m = machineRepository.findById(o.getMachineId()).orElse(null);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("orderId", o.getId());
            row.put("orderNo", o.getOrderNo());
            row.put("machineCode", m == null ? null : m.getCode());
            row.put("sessionNo", s == null ? null : s.getSessionNo());
            row.put("status", o.getStatus().name());
            row.put("evidence", o.getEvidence().name());
            out.add(row);
        }
        return out;
    }

    // ---------------- 人工复核 ----------------

    @Transactional
    public Map<String, Object> manualReview(Long orderId, ReviewRequest req) {
        OrderInfo order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("订单不存在: " + orderId));
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new IllegalStateException("仅待复核订单可人工处理，当前状态: " + order.getStatus());
        }
        DoorSession session = sessionRepository.findById(order.getSessionId()).orElseThrow();
        Machine machine = machineRepository.findById(order.getMachineId()).orElseThrow();
        List<EventLog> events = eventLogRepository.findBySessionIdOrderBySeqAsc(session.getId());
        Reconciliation recon = reconcile(session, machine, events);

        if ("REJECT".equalsIgnoreCase(req.decision())) {
            // 用户未实际拿取 / 误识别：不扣款、不扣库存（关门时本就未扣），订单取消
            order.setStatus(OrderStatus.CANCELLED);
            order.setEvidence(EvidenceLevel.INSUFFICIENT);
            order.setSettledAt(Instant.now());
            orderRepository.save(order);
            return Map.of("orderNo", order.getOrderNo(), "status", order.getStatus().name(),
                    "message", "复核驳回，未扣款");
        }
        if (!"CONFIRM".equalsIgnoreCase(req.decision())) {
            throw new IllegalArgumentException("decision 必须为 CONFIRM 或 REJECT");
        }

        BigDecimal total = order.getTotalAmount() == null ? BigDecimal.ZERO : order.getTotalAmount();
        List<Map<String, Object>> added = new ArrayList<>();
        if (req.items() != null) {
            Map<String, PickedLine> netByBatch = new LinkedHashMap<>();
            for (PickedLine l : recon.all) netByBatch.put(l.batchNo, l);
            for (ReviewRequest.ItemAdjust adj : req.items()) {
                PickedLine line = netByBatch.get(adj.batchNo());
                if (line == null) throw new IllegalArgumentException("会话中无此批次拿取记录: " + adj.batchNo());
                int qty = adj.qty() == null ? line.netQty : Math.max(0, Math.min(adj.qty(), line.netQty));
                if (qty == 0) continue;
                Batch batch = batchRepository.findById(line.batchId).orElseThrow();
                Product product = productRepository.findById(batch.getProductId()).orElseThrow();
                Slot slot = slotRepository.findById(line.slotId).orElseThrow();
                int deduct = Math.min(qty, inventoryService.qtyOfSlotBatch(slot.getId(), batch.getId()));
                if (deduct <= 0) {
                    throw new IllegalStateException("批次 " + adj.batchNo() + " 库存不足，无法确认");
                }
                inventoryService.apply(machine.getId(), slot.getId(), batch.getId(), -deduct,
                        LedgerReason.SALE, "ORDER", order.getId());
                BigDecimal amount = product.getUnitPrice().multiply(BigDecimal.valueOf(deduct));
                total = total.add(amount);
                OrderItem oi = new OrderItem();
                oi.setOrderId(order.getId());
                oi.setProductId(product.getId());
                oi.setBatchId(batch.getId());
                oi.setQty(deduct);
                oi.setUnitPrice(product.getUnitPrice());
                oi.setAmount(amount);
                oi.setSource(ItemSource.MANUAL_REVIEW);
                orderItemRepository.save(oi);
                added.add(Map.of("batchNo", batch.getBatchNo(), "product", product.getName(),
                        "qty", deduct, "amount", amount));
            }
        }

        boolean stillPending = false; // 人工确认后结算；未确认部分视为放行不扣款
        order.setStatus(OrderStatus.CONFIRMED);
        order.setEvidence(EvidenceLevel.SUFFICIENT);
        order.setTotalAmount(total);
        order.setSettledAt(Instant.now());
        orderRepository.save(order);
        return Map.of("orderNo", order.getOrderNo(), "status", order.getStatus().name(),
                "totalAmount", total, "confirmedFromReview", added, "note",
                req.note() == null ? "" : req.note());
    }

    // ---------------- 对账算法 ----------------

    private Reconciliation reconcile(DoorSession session, Machine machine, List<EventLog> events) {
        // 净拿取：key = slotCode|batchNo
        Map<String, int[]> picks = new LinkedHashMap<>();   // [picked, putback]
        Map<String, Long> slotCodeId = new HashMap<>();
        Map<String, Long> batchNoId = new HashMap<>();
        Set<String> jammedSlots = new HashSet<>();

        for (EventLog ev : events) {
            Map<String, Object> p = parse(ev.getPayload());
            String slot = str(p.get("slotCode"));
            String batch = str(p.get("batchNo"));
            if (slot != null) slotRepository.findByMachineIdAndCode(machine.getId(), slot)
                    .ifPresent(s -> slotCodeId.put(slot, s.getId()));
            if (batch != null && findBatchOrNull(batch) != null) batchNoId.put(batch, findBatchOrNull(batch).getId());

            switch (ev.getEventType()) {
                case PICK -> {
                    int q = p.get("qty") == null ? 1 : Integer.parseInt(String.valueOf(p.get("qty")));
                    picks.computeIfAbsent(key(slot, batch), k -> new int[2])[0] += q;
                }
                case PUT_BACK -> {
                    int q = p.get("qty") == null ? 1 : Integer.parseInt(String.valueOf(p.get("qty")));
                    picks.computeIfAbsent(key(slot, batch), k -> new int[2])[1] += q;
                }
                case JAM -> {
                    if (slot != null) jammedSlots.add(slot);
                }
                default -> { }
            }
        }

        List<RecognitionResult> recognitions = recognitionRepository.findBySessionId(session.getId());
        Reconciliation recon = new Reconciliation();
        for (Map.Entry<String, int[]> e : picks.entrySet()) {
            String[] parts = e.getKey().split("\\|", -1);
            String slotCode = parts[0];
            String batchNo = parts[1];
            int net = Math.max(0, e.getValue()[0] - e.getValue()[1]);
            if (net == 0) continue;   // 全部放回
            Long slotId = slotCodeId.get(slotCode);
            Long batchId = batchNoId.get(batchNo);
            if (slotId == null || batchId == null) {
                recon.uncertain.add(new PickedLine(slotCode, null, batchNo, null, net,
                        "货道或批次无法定位，证据不足"));
                continue;
            }
            PickedLine line = new PickedLine(slotCode, slotId, batchNo, batchId, net, null);

            if (jammedSlots.contains(slotCode)) {
                recon.uncertain.add(line.withReason("货道卡货，商品未交付"));
                continue;
            }
            if (e.getValue()[1] > 0) {
                line.notes.add("拿起后放回 " + e.getValue()[1] + " 件，按净拿取 " + net + " 件结算");
            }
            // 质量锁定（温控等）批次不可自动售卖
            boolean held = !holdRepository.findByBatchIdAndStatus(batchId, HoldStatus.ACTIVE).isEmpty();
            if (held) {
                recon.uncertain.add(line.withReason("批次处于质量锁定（温控/疑似），不可自动售卖"));
                continue;
            }
            evaluateEvidence(line, recognitions);
            recon.all.add(line);
            if (line.reason == null) recon.sufficient.add(line);
            else recon.uncertain.add(line);
        }
        return recon;
    }

    private void evaluateEvidence(PickedLine line, List<RecognitionResult> recognitions) {
        double best = 0.0;
        Set<String> supportingMethods = new HashSet<>();
        int conflictingHighConf = 0;
        int matchingHighConf = 0;
        for (RecognitionResult rr : recognitions) {
            Long rrBatch = rr.getBatchId();
            double c = rr.getConfidence().doubleValue();
            if (rrBatch != null && rrBatch.equals(line.batchId) && c >= confidenceThreshold) {
                matchingHighConf++;
                supportingMethods.add(rr.getMethod().name());
                best = Math.max(best, c);
            } else if (rr.getProductId() != null && c >= confidenceThreshold) {
                // 同会话高置信度但识别成别的批次/商品 → 多模态冲突
                conflictingHighConf++;
            }
        }
        line.bestConfidence = best;
        line.supportingMethods.addAll(supportingMethods);
        if (matchingHighConf == 0) {
            line.reason = "缺少达到置信度阈值的识别结果（阈值 " + confidenceThreshold + "），证据不足";
        } else if (conflictingHighConf > 0) {
            line.reason = "多模态识别结果冲突，需人工复核视频";
        } else {
            line.notes.add("佐证方式: " + String.join("+", supportingMethods) + "，最高置信度 " + best);
        }
    }

    // ---------------- 视图与辅助 ----------------

    private Map<String, Object> itemView(Slot slot, Batch batch, Product product, int qty,
                                         BigDecimal amount, PickedLine line) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("slotCode", slot.getCode());
        m.put("batchNo", batch.getBatchNo());
        m.put("product", product.getName());
        m.put("qty", qty);
        m.put("unitPrice", product.getUnitPrice());
        m.put("amount", amount);
        m.put("evidence", String.join("+", line.supportingMethods));
        m.put("notes", line.notes);
        return m;
    }

    private Map<String, Object> lineView(PickedLine line) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("slotCode", line.slotCode);
        m.put("batchNo", line.batchNo);
        m.put("netQty", line.netQty);
        m.put("reason", line.reason);
        m.put("bestConfidence", line.bestConfidence);
        m.put("supportingMethods", line.supportingMethods);
        return m;
    }

    private void publishOrderEvent(OrderInfo order, Machine machine,
                                   List<Map<String, Object>> confirmed, List<Map<String, Object>> pending) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderNo", order.getOrderNo());
        payload.put("sessionNo", order.getOrderNo());
        payload.put("machineCode", machine.getCode());
        payload.put("status", order.getStatus().name());
        payload.put("evidence", order.getEvidence().name());
        payload.put("totalAmount", order.getTotalAmount());
        payload.put("confirmedItems", confirmed);
        payload.put("pendingReviewItems", pending);
        payload.put("occurredAt", Instant.now().toString());
        eventPublisher.publish(new DomainEvent(Topics.ORDER, order.getOrderNo(), "OrderClosed",
                json(payload)));
    }

    private EventLog appendEvent(DoorSession session, Machine machine, EventType type, DetectMethod method,
                                 Double confidence, Map<String, Object> payload) {
        EventLog log = new EventLog();
        log.setSessionId(session.getId());
        log.setMachineId(machine.getId());
        log.setSeq((int) eventLogRepository.countBySessionId(session.getId()) + 1);
        log.setEventType(type);
        log.setMethod(method);
        log.setConfidence(confidence == null ? null : BigDecimal.valueOf(confidence));
        log.setPayload(json(payload));
        log.setOccurredAt(Instant.now());
        return eventLogRepository.save(log);
    }

    private Map<String, Object> parse(String s) {
        try {
            return mapper.readValue(s, Map.class);
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private String json(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private String str(Object o) { return o == null ? null : String.valueOf(o); }
    private String key(String slot, String batch) {
        return (slot == null ? "?" : slot) + "|" + (batch == null ? "?" : batch);
    }

    private Machine findMachine(String code) {
        return machineRepository.findAll().stream().filter(m -> m.getCode().equals(code)).findFirst()
                .orElseThrow(() -> new EntityNotFoundException("货柜不存在: " + code));
    }

    private Batch findBatchOrNull(String batchNo) {
        return batchRepository.findAll().stream().filter(b -> b.getBatchNo().equals(batchNo)).findFirst()
                .orElse(null);
    }

    private DoorSession getSession(Long id) {
        return sessionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("开门会话不存在: " + id));
    }

    // ---------------- 内部结构 ----------------

    private static class Reconciliation {
        List<PickedLine> all = new ArrayList<>();
        List<PickedLine> sufficient = new ArrayList<>();
        List<PickedLine> uncertain = new ArrayList<>();
    }

    private static class PickedLine {
        final String slotCode;
        final Long slotId;
        final String batchNo;
        final Long batchId;
        final int netQty;
        String reason;
        double bestConfidence;
        Set<String> supportingMethods = new HashSet<>();
        List<String> notes = new ArrayList<>();
        int deductQty;
        BigDecimal amount = BigDecimal.ZERO;

        PickedLine(String slotCode, Long slotId, String batchNo, Long batchId, int netQty, String reason) {
            this.slotCode = slotCode; this.slotId = slotId;
            this.batchNo = batchNo; this.batchId = batchId;
            this.netQty = netQty; this.reason = reason;
        }

        PickedLine withReason(String r) { this.reason = r; return this; }
    }
}
