package com.cabinet.web;

import com.cabinet.domain.StocktakeOrder;
import com.cabinet.repo.StocktakeItemRepository;
import com.cabinet.service.StocktakeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@Tag(name = "盘点", description = "创建盘点单（系统库存快照）、录入实盘、差异审批调整")
@RestController
@RequestMapping("/api/stocktakes")
@RequiredArgsConstructor
public class StocktakeController {

    private final StocktakeService stocktakeService;
    private final StocktakeItemRepository itemRepository;

    public record CreateRequest(String cabinetNo) {}
    public record CountRequest(List<StocktakeService.CountLine> counts) {}
    public record AdjustRequest(Boolean approve, String remark) {}

    @Operation(summary = "创建盘点单并生成系统库存快照")
    @PostMapping
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN','REPLENISHER')")
    public StocktakeOrder create(@RequestBody CreateRequest req, Principal principal) {
        return stocktakeService.create(req.cabinetNo(), principal.getName());
    }

    @Operation(summary = "录入实盘数量并计算差异（有差异进入待调整）")
    @PostMapping("/{stocktakeNo}/count")
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN','REPLENISHER')")
    public StocktakeOrder count(@PathVariable String stocktakeNo, @RequestBody CountRequest req) {
        return stocktakeService.count(stocktakeNo, req.counts());
    }

    @Operation(summary = "盘点差异审批调整：盘亏记报损、台账 ADJUST_MINUS；盘盈 ADJUST_PLUS")
    @PostMapping("/{stocktakeNo}/adjust")
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN')")
    public StocktakeOrder adjust(@PathVariable String stocktakeNo, @RequestBody AdjustRequest req) {
        return stocktakeService.adjust(stocktakeNo,
                req.approve() == null || req.approve(), req.remark());
    }

    @Operation(summary = "盘点明细（系统数/实盘数/差异）")
    @GetMapping("/{stocktakeId}/items")
    @PreAuthorize("isAuthenticated()")
    public Object items(@PathVariable Long stocktakeId) {
        return itemRepository.findByStocktakeId(stocktakeId);
    }
}
