package com.uvend.web;

import com.uvend.service.ComplaintService;
import com.uvend.web.dto.ComplaintDecisionRequest;
import com.uvend.web.dto.ComplaintRequest;
import com.uvend.web.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "投诉与售后证据回放")
@RestController
@RequestMapping("/api/complaints")
public class ComplaintController {

    private final ComplaintService complaintService;

    public ComplaintController(ComplaintService complaintService) {
        this.complaintService = complaintService;
    }

    @Operation(summary = "用户就订单发起投诉（误扣款/未拿取/质量）")
    @PreAuthorize("hasAnyRole('CUSTOMER','ADMIN')")
    @PostMapping
    public Map<String, Object> create(@Valid @RequestBody ComplaintRequest req) {
        return complaintService.create(SecurityUtils.currentUserId(), req);
    }

    @Operation(summary = "客服投诉列表")
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN')")
    @GetMapping
    public List<Map<String, Object>> list(@RequestParam(required = false) String status) {
        return complaintService.list(status);
    }

    @Operation(summary = "证据回放：开门记录、事件时间线、识别结果、视频片段、会话前后库存变化")
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN')")
    @GetMapping("/{complaintNo}/evidence")
    public Map<String, Object> evidence(@PathVariable String complaintNo) {
        return complaintService.evidenceBundle(complaintNo);
    }

    @Operation(summary = "客服处理：REFUND（全额/部分金额/按明细）或 REJECT")
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN')")
    @PostMapping("/{complaintNo}/decision")
    public Map<String, Object> decide(@PathVariable String complaintNo,
                                      @Valid @RequestBody ComplaintDecisionRequest req) {
        return complaintService.decide(complaintNo, SecurityUtils.currentUserId(), req);
    }
}
