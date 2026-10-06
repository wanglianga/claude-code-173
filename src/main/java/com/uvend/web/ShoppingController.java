package com.uvend.web;

import com.uvend.service.ShoppingService;
import com.uvend.web.dto.EventBatchRequest;
import com.uvend.web.dto.OpenSessionRequest;
import com.uvend.web.dto.ReviewRequest;
import com.uvend.web.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "购物识别与关门对账")
@RestController
@RequestMapping("/api/shopping")
public class ShoppingController {

    private final ShoppingService shoppingService;

    public ShoppingController(ShoppingService shoppingService) {
        this.shoppingService = shoppingService;
    }

    @Operation(summary = "用户扫码开门，创建开门会话")
    @PreAuthorize("hasAnyRole('CUSTOMER','ADMIN')")
    @PostMapping("/sessions")
    public Map<String, Object> open(@Valid @RequestBody OpenSessionRequest req) {
        return shoppingService.openSession(SecurityUtils.currentUserId(), req);
    }

    @Operation(summary = "上报柜内事件（PICK/PUT_BACK/RECOGNITION/WEIGHT/RFID/JAM/TEMPERATURE）")
    @PreAuthorize("hasAnyRole('CUSTOMER','STAFF','OPERATOR','ADMIN')")
    @PostMapping("/sessions/{sessionId}/events")
    public Map<String, Object> events(@PathVariable Long sessionId,
                                      @Valid @RequestBody EventBatchRequest batch) {
        return shoppingService.reportEvents(sessionId, batch);
    }

    @Operation(summary = "关门并多模态对账（放回净额、卡货、低置信度/冲突→证据不足挂起）")
    @PreAuthorize("hasAnyRole('CUSTOMER','STAFF','OPERATOR','ADMIN')")
    @PostMapping("/sessions/{sessionId}/close")
    public Map<String, Object> close(@PathVariable Long sessionId,
                                     @RequestParam(required = false) String videoClipUrl) {
        return shoppingService.closeSession(sessionId, videoClipUrl);
    }

    @Operation(summary = "待人工复核订单列表（证据不足）")
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN')")
    @GetMapping("/orders/pending")
    public java.util.List<Map<String, Object>> pending(@RequestParam(required = false) String machineCode) {
        return shoppingService.pendingOrders(machineCode);
    }

    @Operation(summary = "运营人工复核证据不足订单：CONFIRM 扣款 / REJECT 取消")
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN')")
    @PostMapping("/orders/{orderId}/review")
    public Map<String, Object> review(@PathVariable Long orderId, @Valid @RequestBody ReviewRequest req) {
        return shoppingService.manualReview(orderId, req);
    }
}
