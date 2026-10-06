package com.cabinet.web;

import com.cabinet.domain.SettlementBatch;
import com.cabinet.repo.SettlementBatchRepository;
import com.cabinet.service.SettlementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Tag(name = "财务结算", description = "按供应商周期依据真实售卖与报损生成结算单、确认、标记付款")
@RestController
@RequestMapping("/api/finance/settlements")
@RequiredArgsConstructor
public class SettlementController {

    private final SettlementService settlementService;
    private final SettlementBatchRepository settlementRepository;

    public record GenerateRequest(Long supplierId,
                                  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
                                  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {}

    @Operation(summary = "生成供应商周期结算草稿（按批次明细）")
    @PostMapping("/generate")
    @PreAuthorize("hasAnyRole('FINANCE','ADMIN')")
    public SettlementBatch generate(@RequestBody GenerateRequest req) {
        return settlementService.generate(req.supplierId(), req.start(), req.end());
    }

    @Operation(summary = "确认结算单")
    @PostMapping("/{settlementNo}/confirm")
    @PreAuthorize("hasAnyRole('FINANCE','ADMIN')")
    public SettlementBatch confirm(@PathVariable String settlementNo) {
        return settlementService.confirm(settlementNo);
    }

    @Operation(summary = "标记已付款")
    @PostMapping("/{settlementNo}/pay")
    @PreAuthorize("hasAnyRole('FINANCE','ADMIN')")
    public SettlementBatch pay(@PathVariable String settlementNo) {
        return settlementService.markPaid(settlementNo);
    }

    @Operation(summary = "结算单批次明细")
    @GetMapping("/{settlementId}/items")
    @PreAuthorize("hasAnyRole('FINANCE','OPERATOR','ADMIN')")
    public Object items(@PathVariable Long settlementId) {
        return settlementService.items(settlementId);
    }

    @Operation(summary = "结算单列表（可按供应商筛选）")
    @GetMapping
    @PreAuthorize("hasAnyRole('FINANCE','OPERATOR','ADMIN')")
    public List<SettlementBatch> list(@RequestParam(required = false) Long supplierId) {
        return supplierId == null ? settlementRepository.findAll()
                : settlementRepository.findBySupplierIdOrderByPeriodStartDesc(supplierId);
    }
}
