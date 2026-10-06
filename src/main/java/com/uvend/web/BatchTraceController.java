package com.uvend.web;

import com.uvend.service.BatchTraceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "批次全链路追踪")
@RestController
@RequestMapping("/api/batches")
public class BatchTraceController {

    private final BatchTraceService batchTraceService;

    public BatchTraceController(BatchTraceService batchTraceService) {
        this.batchTraceService = batchTraceService;
    }

    @Operation(summary = "按批次查看补货/售卖/下架/报损/结算轨迹与损耗原因（运营与供应商）")
    @PreAuthorize("hasAnyRole('OPERATOR','FINANCE','SUPPLIER','ADMIN')")
    @GetMapping("/{batchNo}/trace")
    public Map<String, Object> trace(@PathVariable String batchNo) {
        return batchTraceService.trace(batchNo);
    }
}
