package com.uvend.web;

import com.uvend.domain.entity.OffShelfTask;
import com.uvend.service.ExpiryService;
import com.uvend.service.OffShelfService;
import com.uvend.web.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "临期与下架")
@RestController
@RequestMapping("/api/off-shelf")
public class OffShelfController {

    private final ExpiryService expiryService;
    private final OffShelfService offShelfService;

    public OffShelfController(ExpiryService expiryService, OffShelfService offShelfService) {
        this.expiryService = expiryService;
        this.offShelfService = offShelfService;
    }

    @Operation(summary = "手动触发临期/过期扫描（每日 02:00 也会自动执行）")
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN')")
    @PostMapping("/scan")
    public Map<String, Object> scan() {
        return Map.of("createdTasks", expiryService.scan());
    }

    @Operation(summary = "待下架任务列表（可按柜机过滤）")
    @PreAuthorize("hasAnyRole('OPERATOR','STAFF','ADMIN')")
    @GetMapping("/tasks")
    public List<Map<String, Object>> pending(@RequestParam(required = false) String machineCode) {
        return offShelfService.pending(machineCode).stream().map(t -> Map.<String, Object>of(
                "taskNo", t.getTaskNo(), "machineId", t.getMachineId(), "slotId", t.getSlotId(),
                "batchId", t.getBatchId(), "qty", t.getQty(), "reason", t.getReason().name(),
                "status", t.getStatus().name())).toList();
    }

    @Operation(summary = "执行下架：convertToLoss=true 时转报损（过期/损坏/温控），批次可结算")
    @PreAuthorize("hasAnyRole('OPERATOR','STAFF','ADMIN')")
    @PostMapping("/tasks/{taskNo}/complete")
    public Map<String, Object> complete(@PathVariable String taskNo,
                                        @RequestParam(defaultValue = "false") boolean convertToLoss,
                                        @RequestParam(required = false) String note) {
        return offShelfService.complete(taskNo, SecurityUtils.currentUserId(), convertToLoss, note);
    }

    @Operation(summary = "放错货道商品归位到正确货道")
    @PreAuthorize("hasAnyRole('OPERATOR','STAFF','ADMIN')")
    @PostMapping("/tasks/{taskNo}/relocate")
    public Map<String, Object> relocate(@PathVariable String taskNo, @RequestParam String targetSlotCode) {
        return offShelfService.relocate(taskNo, targetSlotCode, SecurityUtils.currentUserId());
    }

    @Operation(summary = "跳过下架任务（如现场核实商品已售出）")
    @PreAuthorize("hasAnyRole('OPERATOR','STAFF','ADMIN')")
    @PostMapping("/tasks/{taskNo}/skip")
    public Map<String, Object> skip(@PathVariable String taskNo,
                                    @RequestParam(required = false) String note) {
        return offShelfService.skip(taskNo, SecurityUtils.currentUserId(), note);
    }
}
