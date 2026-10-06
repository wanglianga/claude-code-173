package com.uvend.web;

import com.uvend.service.AssortmentService;
import com.uvend.web.dto.AssortmentRequest;
import com.uvend.web.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "点位品类调整")
@RestController
@RequestMapping("/api/assortment")
public class AssortmentController {

    private final AssortmentService assortmentService;

    public AssortmentController(AssortmentService assortmentService) {
        this.assortmentService = assortmentService;
    }

    @Operation(summary = "按点位调整货道陈列商品（留痕；校验温控匹配）")
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN')")
    @PostMapping("/adjust")
    public Map<String, Object> adjust(@Valid @RequestBody AssortmentRequest req) {
        return assortmentService.adjust(SecurityUtils.currentUserId(), req.machineCode(),
                req.slotCode(), req.newProductSku(), req.reason());
    }
}
