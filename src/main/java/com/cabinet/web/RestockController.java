package com.cabinet.web;

import com.cabinet.domain.RestockItem;
import com.cabinet.domain.RestockOrder;
import com.cabinet.service.RestockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;

@Tag(name = "补货", description = "补货员到柜登记、上架（批次/保质期/货道/温度/照片）、完成")
@RestController
@RequestMapping("/api/restock")
@RequiredArgsConstructor
public class RestockController {

    private final RestockService restockService;

    public record ArriveRequest(@NotBlank String cabinetNo, String photoUrls, String remark) {}

    public record PlaceItemRequest(
            @NotBlank String sku,
            @NotBlank String batchNo,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate productionDate,
            @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiryDate,
            @NotNull @Positive Integer qty,
            @NotBlank String channelCode,
            String photoUrl,
            Double cabinetTempC) {}

    @Operation(summary = "补货员到达柜机，创建补货单")
    @PostMapping("/arrive")
    @PreAuthorize("hasAnyRole('REPLENISHER','OPERATOR','ADMIN')")
    public RestockOrder arrive(@RequestBody ArriveRequest req, Principal principal) {
        return restockService.arrive(req.cabinetNo(), principal.getName(),
                req.photoUrls(), req.remark());
    }

    @Operation(summary = "上架一条补货明细（校验放错货道/温区/保质期，形成库存与台账）")
    @PostMapping("/{orderNo}/items")
    @PreAuthorize("hasAnyRole('REPLENISHER','OPERATOR','ADMIN')")
    public RestockItem place(@PathVariable String orderNo, @RequestBody PlaceItemRequest req) {
        return restockService.placeItem(orderNo, req.sku(), req.batchNo(),
                req.productionDate(), req.expiryDate(), req.qty(),
                req.channelCode(), req.photoUrl(), req.cabinetTempC());
    }

    @Operation(summary = "完成补货（存在放错货道等告警时状态为 COMPLETED_MISMATCH）")
    @PostMapping("/{orderNo}/complete")
    @PreAuthorize("hasAnyRole('REPLENISHER','OPERATOR','ADMIN')")
    public RestockOrder complete(@PathVariable String orderNo,
                                 @RequestBody(required = false) java.util.Map<String, String> body) {
        return restockService.complete(orderNo, body == null ? null : body.get("remark"));
    }

    @Operation(summary = "查看补货明细")
    @GetMapping("/{orderId}/items")
    @PreAuthorize("isAuthenticated()")
    public List<RestockItem> items(@PathVariable Long orderId) {
        return restockService.items(orderId);
    }
}
