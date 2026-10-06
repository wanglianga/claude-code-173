package com.cabinet.web;

import com.cabinet.domain.DoorSession;
import com.cabinet.domain.OrderRecord;
import com.cabinet.domain.RecognitionEvent;
import com.cabinet.repo.DoorSessionRepository;
import com.cabinet.repo.OrderRecordRepository;
import com.cabinet.repo.RecognitionEventRepository;
import com.cabinet.service.SessionSettlementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "开门会话与识别复核", description = "会话查询、识别事件查看、存疑订单人工复核")
@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final DoorSessionRepository sessionRepository;
    private final RecognitionEventRepository eventRepository;
    private final OrderRecordRepository orderRepository;
    private final SessionSettlementService settlementService;

    @Operation(summary = "会话详情")
    @GetMapping("/{sessionNo}")
    @PreAuthorize("isAuthenticated()")
    public DoorSession get(@PathVariable String sessionNo) {
        return sessionRepository.findBySessionNo(sessionNo).orElseThrow();
    }

    @Operation(summary = "会话的全部识别事件（重量/视觉/RFID 多源）")
    @GetMapping("/{sessionNo}/events")
    @PreAuthorize("isAuthenticated()")
    public List<RecognitionEvent> events(@PathVariable String sessionNo) {
        DoorSession s = sessionRepository.findBySessionNo(sessionNo).orElseThrow();
        return eventRepository.findBySessionIdOrderByDetectedAtAsc(s.getId());
    }

    @Operation(summary = "会话关联订单")
    @GetMapping("/{sessionNo}/order")
    @PreAuthorize("isAuthenticated()")
    public OrderRecord order(@PathVariable String sessionNo) {
        DoorSession s = sessionRepository.findBySessionNo(sessionNo).orElseThrow();
        return orderRepository.findBySessionId(s.getId()).orElse(null);
    }

    @Operation(summary = "按柜机查看最近会话")
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<DoorSession> byCabinet(@RequestParam String cabinetNo,
                                       @RequestParam(required = false) String userCode) {
        if (userCode != null && !userCode.isBlank()) {
            return sessionRepository.findByUserCodeOrderByOpenedAtDesc(userCode);
        }
        return sessionRepository.findByCabinetCabinetNoOrderByOpenedAtDesc(cabinetNo);
    }

    public record ReviewBody(List<SessionSettlementService.ConfirmedLine> lines, String note) {}

    @Operation(summary = "人工复核确认：执行扣库扣款（lines 为空则按融合结果确认）")
    @PostMapping("/{sessionNo}/review/confirm")
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN','CUSTOMER_SERVICE')")
    public OrderRecord confirm(@PathVariable String sessionNo, @RequestBody(required = false) ReviewBody body) {
        return settlementService.confirmReview(sessionNo,
                body == null ? null : body.lines(),
                body == null ? null : body.note());
    }

    @Operation(summary = "人工复核否定：取消订单、不扣款")
    @PostMapping("/{sessionNo}/review/reject")
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN','CUSTOMER_SERVICE')")
    public OrderRecord reject(@PathVariable String sessionNo, @RequestBody Map<String, String> body) {
        return settlementService.rejectReview(sessionNo, body.getOrDefault("note", "复核取消"));
    }
}
