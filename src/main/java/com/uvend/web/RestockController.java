package com.uvend.web;

import com.uvend.service.RestockService;
import com.uvend.web.dto.RestockRequest;
import com.uvend.web.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "补货")
@RestController
@RequestMapping("/api/staff")
public class RestockController {

    private final RestockService restockService;

    public RestockController(RestockService restockService) {
        this.restockService = restockService;
    }

    @Operation(summary = "补货员提交补货（柜机/批次/保质期/数量/货道/温控/照片；处理卡货、放错货道、温度锁定）")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    @PostMapping("/restock")
    public Map<String, Object> restock(@Valid @RequestBody RestockRequest req) {
        return restockService.restock(SecurityUtils.currentUserId(), req);
    }
}
