package com.uvend.web;

import com.uvend.service.TemperatureService;
import com.uvend.web.dto.TemperatureRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "温控")
@RestController
@RequestMapping("/api/temperature")
public class TemperatureController {

    private final TemperatureService temperatureService;

    public TemperatureController(TemperatureService temperatureService) {
        this.temperatureService = temperatureService;
    }

    @Operation(summary = "设备/IoT 上报温度；越界自动告警、锁柜并冻结在柜批次")
    @PreAuthorize("hasAnyRole('OPERATOR','STAFF','ADMIN')")
    @PostMapping("/reports")
    public Map<String, Object> report(@Valid @RequestBody TemperatureRequest req) {
        return temperatureService.report(req);
    }

    @Operation(summary = "处置温控告警：RESTORED/RELEASED 放行解冻，CONDEMNED 在柜批次报损下架")
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN')")
    @PostMapping("/alerts/{alertNo}/resolve")
    public Map<String, Object> resolve(@PathVariable String alertNo,
                                       @RequestParam String resolution,
                                       @RequestParam(required = false) String note) {
        return temperatureService.resolve(alertNo, resolution, note);
    }
}
