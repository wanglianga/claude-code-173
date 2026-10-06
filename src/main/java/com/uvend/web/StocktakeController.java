package com.uvend.web;

import com.uvend.service.StocktakeService;
import com.uvend.web.dto.StocktakeRequest;
import com.uvend.web.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "盘点")
@RestController
@RequestMapping("/api/stocktake")
public class StocktakeController {

    private final StocktakeService stocktakeService;

    public StocktakeController(StocktakeService stocktakeService) {
        this.stocktakeService = stocktakeService;
    }

    @Operation(summary = "创建盘点单并快照系统库存")
    @PreAuthorize("hasAnyRole('OPERATOR','STAFF','ADMIN')")
    @PostMapping("/open")
    public Map<String, Object> open(@Valid @RequestBody StocktakeRequest req) {
        return stocktakeService.open(req.machineCode(), SecurityUtils.currentUserId(), req.note());
    }

    @Operation(summary = "录入实盘数量（自动算差异；账外批次识别放错货道）")
    @PreAuthorize("hasAnyRole('OPERATOR','STAFF','ADMIN')")
    @PostMapping("/{stocktakeNo}/count")
    public Map<String, Object> count(@PathVariable String stocktakeNo,
                                     @Valid @RequestBody StocktakeRequest req) {
        return stocktakeService.count(stocktakeNo, req.counts());
    }

    @Operation(summary = "差异处置：LOSS 盘亏报损 / FOUND_MISPLACED 找到错放 / SYSTEM_CORRECTED 调账")
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN')")
    @PostMapping("/{stocktakeNo}/items/{itemId}/resolve")
    public Map<String, Object> resolve(@PathVariable String stocktakeNo, @PathVariable Long itemId,
                                       @RequestParam String resolution,
                                       @RequestParam(required = false) String note) {
        return stocktakeService.resolve(stocktakeNo, itemId, resolution, note);
    }

    @Operation(summary = "关闭盘点单（差异须全部处置）")
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN')")
    @PostMapping("/{stocktakeNo}/close")
    public Map<String, Object> close(@PathVariable String stocktakeNo) {
        return stocktakeService.close(stocktakeNo);
    }

    @Operation(summary = "盘点单详情")
    @PreAuthorize("hasAnyRole('OPERATOR','STAFF','ADMIN')")
    @GetMapping("/{stocktakeNo}")
    public Map<String, Object> detail(@PathVariable String stocktakeNo) {
        return stocktakeService.detail(stocktakeNo);
    }
}
