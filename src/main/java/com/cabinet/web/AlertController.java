package com.cabinet.web;

import com.cabinet.domain.Alert;
import com.cabinet.domain.enums.AlertStatus;
import com.cabinet.repo.AlertRepository;
import com.cabinet.service.AlertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "告警", description = "放错货道/卡货/温控/临期/盘点差异/识别存疑告警与处置")
@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertRepository alertRepository;
    private final AlertService alertService;

    @Operation(summary = "告警列表（默认仅未关闭，status=ALL 查看全部）")
    @GetMapping
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN','REPLENISHER','CUSTOMER_SERVICE')")
    public List<Alert> list(@RequestParam(defaultValue = "OPEN") String status) {
        if ("ALL".equalsIgnoreCase(status)) return alertRepository.findAll();
        return alertRepository.findByStatusOrderByIdDesc(AlertStatus.valueOf(status.toUpperCase()));
    }

    @Operation(summary = "关闭告警")
    @PostMapping("/{id}/resolve")
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN')")
    public Map<String, Object> resolve(@PathVariable Long id, @RequestBody Map<String, String> body) {
        alertService.resolve(id, body.getOrDefault("resolution", "已处理"));
        return Map.of("resolved", id);
    }
}
