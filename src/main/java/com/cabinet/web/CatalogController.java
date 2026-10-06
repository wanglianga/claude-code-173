package com.cabinet.web;

import com.cabinet.domain.*;
import com.cabinet.exception.ApiException;
import com.cabinet.repo.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "主数据与库存查询",
     description = "柜机/货道/批次库存查询；运营按点位调整货道品类")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CatalogController {

    private final CabinetRepository cabinetRepository;
    private final ChannelRepository channelRepository;
    private final ChannelStockRepository stockRepository;
    private final ProductRepository productRepository;
    private final SupplierRepository supplierRepository;
    private final ProductBatchRepository batchRepository;
    private final OrderRecordRepository orderRepository;
    private final TempLogRepository tempLogRepository;

    @Operation(summary = "柜机列表")
    @GetMapping("/cabinets")
    @PreAuthorize("isAuthenticated()")
    public List<Cabinet> cabinets() {
        return cabinetRepository.findAll();
    }

    @Operation(summary = "柜机详情：货道与各货道批次库存（含隔离数量）")
    @GetMapping("/cabinets/{cabinetNo}")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> cabinet(@PathVariable String cabinetNo) {
        Cabinet c = cabinetRepository.findByCabinetNo(cabinetNo)
                .orElseThrow(() -> ApiException.notFound("柜机不存在"));
        List<Channel> channels = channelRepository.findByCabinetId(c.getId());
        return Map.of(
                "cabinet", c,
                "point", c.getPoint(),
                "channels", channels,
                "stocks", stockRepository.findByCabinetId(c.getId()));
    }

    public record PlannedProductRequest(String sku) {}

    @Operation(summary = "运营按点位调整品类：修改货道计划商品")
    @PutMapping("/channels/{channelId}/planned-product")
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN')")
    public Channel changePlannedProduct(@PathVariable Long channelId,
                                        @RequestBody PlannedProductRequest req) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> ApiException.notFound("货道不存在"));
        Product p = productRepository.findBySku(req.sku())
                .orElseThrow(() -> ApiException.badRequest("商品不存在"));
        channel.setPlannedProduct(p);
        return channelRepository.save(channel);
    }

    @Operation(summary = "商品列表")
    @GetMapping("/products")
    @PreAuthorize("isAuthenticated()")
    public List<Product> products() {
        return productRepository.findAll();
    }

    @Operation(summary = "供应商列表")
    @GetMapping("/suppliers")
    @PreAuthorize("isAuthenticated()")
    public List<Supplier> suppliers() {
        return supplierRepository.findAll();
    }

    @Operation(summary = "批次列表")
    @GetMapping("/batches")
    @PreAuthorize("isAuthenticated()")
    public List<ProductBatch> batches() {
        return batchRepository.findAll();
    }

    @Operation(summary = "订单列表（可按柜机筛选）")
    @GetMapping("/orders")
    @PreAuthorize("hasAnyRole('OPERATOR','FINANCE','CUSTOMER_SERVICE','ADMIN')")
    public List<OrderRecord> orders(@RequestParam(required = false) String cabinetNo) {
        if (cabinetNo == null) return orderRepository.findAll();
        Cabinet c = cabinetRepository.findByCabinetNo(cabinetNo)
                .orElseThrow(() -> ApiException.notFound("柜机不存在"));
        return orderRepository.findByCabinetId(c.getId());
    }

    @Operation(summary = "柜机最近温度记录")
    @GetMapping("/cabinets/{cabinetNo}/temperatures")
    @PreAuthorize("isAuthenticated()")
    public List<TempLog> temperatures(@PathVariable String cabinetNo) {
        Cabinet c = cabinetRepository.findByCabinetNo(cabinetNo)
                .orElseThrow(() -> ApiException.notFound("柜机不存在"));
        return tempLogRepository.findTop200ByCabinetIdOrderByRecordedAtDesc(c.getId());
    }
}
