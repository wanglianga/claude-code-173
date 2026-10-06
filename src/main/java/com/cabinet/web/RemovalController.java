package com.cabinet.web;

import com.cabinet.domain.RemovalOrder;
import com.cabinet.domain.enums.Disposition;
import com.cabinet.domain.enums.RemovalReason;
import com.cabinet.exception.ApiException;
import com.cabinet.repo.CabinetRepository;
import com.cabinet.repo.RemovalItemRepository;
import com.cabinet.repo.RemovalOrderRepository;
import com.cabinet.service.RemovalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@Tag(name = "临期下架与报损", description = "临期扫描、手工下架（温控/卡货/放错货道）、执行下架与报废")
@RestController
@RequestMapping("/api/removals")
@RequiredArgsConstructor
public class RemovalController {

    private final RemovalService removalService;
    private final RemovalItemRepository removalItemRepository;
    private final RemovalOrderRepository removalOrderRepository;
    private final CabinetRepository cabinetRepository;

    public record ManualRequest(String cabinetNo, RemovalReason reason,
                                List<RemovalService.ManualRemovalLine> items) {}

    public record ExecuteRequest(Disposition disposition, String remark) {}

    @Operation(summary = "下架单列表（可按柜机筛选）")
    @GetMapping
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN','REPLENISHER','FINANCE')")
    public List<RemovalOrder> list(@RequestParam(required = false) String cabinetNo) {
        List<RemovalOrder> all = removalOrderRepository
                .findAll(org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Direction.DESC, "id"));
        if (cabinetNo == null || cabinetNo.isBlank()) return all;
        return all.stream()
                .filter(r -> r.getCabinet() != null
                        && cabinetNo.equals(r.getCabinet().getCabinetNo()))
                .toList();
    }

    @Operation(summary = "手动触发临期/过期扫描，返回生成的下架单数量")
    @PostMapping("/scan-expiry")
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN')")
    public Map<String, Object> scan(@RequestParam(defaultValue = "2") int nearExpiryDays) {
        int n = removalService.scanExpiry(nearExpiryDays);
        return Map.of("createdRemovalOrders", n);
    }

    @Operation(summary = "手工创建下架单（TEMP_ABUSE/DAMAGED/WRONG_CHANNEL/RECALLED）")
    @PostMapping
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN','REPLENISHER')")
    public RemovalOrder create(@RequestBody ManualRequest req) {
        var cabinet = cabinetRepository.findByCabinetNo(req.cabinetNo())
                .orElseThrow(() -> ApiException.notFound("柜机不存在"));
        return removalService.createManualOrder(cabinet, req.reason(), req.items());
    }

    @Operation(summary = "执行下架：扣库存、台账；SCRAP 去向同时登记报损")
    @PostMapping("/{removalNo}/execute")
    @PreAuthorize("hasAnyRole('REPLENISHER','OPERATOR','ADMIN')")
    public RemovalOrder execute(@PathVariable String removalNo,
                                @RequestBody ExecuteRequest req, Principal principal) {
        Disposition d = req.disposition() != null ? req.disposition() : Disposition.SCRAP;
        return removalService.execute(removalNo, principal.getName(), d, req.remark());
    }

    @Operation(summary = "查看下架明细")
    @GetMapping("/{removalId}/items")
    @PreAuthorize("isAuthenticated()")
    public Object items(@PathVariable Long removalId) {
        return removalItemRepository.findByRemovalOrderId(removalId);
    }
}
