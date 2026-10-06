package com.uvend.web;

import com.uvend.domain.repo.MachineRepository;
import com.uvend.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "运营分析")
@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final MachineRepository machineRepository;

    public AnalyticsController(AnalyticsService analyticsService, MachineRepository machineRepository) {
        this.analyticsService = analyticsService;
        this.machineRepository = machineRepository;
    }

    @Operation(summary = "单柜销量/损耗/临期率/商品排行")
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN')")
    @GetMapping("/machines/{machineCode}")
    public Map<String, Object> machine(@PathVariable String machineCode) {
        Long id = machineRepository.findAll().stream()
                .filter(m -> m.getCode().equals(machineCode)).findFirst()
                .orElseThrow(() -> new java.util.NoSuchElementException("货柜不存在: " + machineCode)).getId();
        return analyticsService.machineReport(id);
    }

    @Operation(summary = "按点位类型（社区/办公楼/医院）汇总销量损耗临期率")
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN')")
    @GetMapping("/locations")
    public List<Map<String, Object>> locations() {
        return analyticsService.locationSummary();
    }
}
