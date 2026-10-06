package com.uvend.web;

import com.uvend.service.SettlementService;
import com.uvend.web.dto.SettlementRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "财务结算")
@RestController
@RequestMapping("/api/finance")
public class SettlementController {

    private final SettlementService settlementService;

    public SettlementController(SettlementService settlementService) {
        this.settlementService = settlementService;
    }

    @Operation(summary = "按供应商+周期生成结算（真实售卖扣除退款；供应商承担报损按成本扣减，按批次明细）")
    @PreAuthorize("hasAnyRole('FINANCE','ADMIN')")
    @PostMapping("/settlements")
    public Map<String, Object> generate(@Valid @RequestBody SettlementRequest req) {
        return settlementService.generate(req.supplierId(), req.periodStart(), req.periodEnd());
    }

    @Operation(summary = "确认结算单")
    @PreAuthorize("hasAnyRole('FINANCE','ADMIN')")
    @PostMapping("/settlements/{settlementNo}/confirm")
    public Map<String, Object> confirm(@PathVariable String settlementNo) {
        return settlementService.confirm(settlementNo);
    }

    @Operation(summary = "结算单详情（含批次明细）")
    @PreAuthorize("hasAnyRole('FINANCE','SUPPLIER','ADMIN')")
    @GetMapping("/settlements/{settlementNo}")
    public Map<String, Object> detail(@PathVariable String settlementNo) {
        return settlementService.detail(settlementNo);
    }
}
