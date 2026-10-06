package com.uvend.web;

import com.uvend.domain.entity.*;
import com.uvend.domain.repo.*;
import com.uvend.web.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Tag(name = "查询")
@RestController
@RequestMapping("/api/query")
public class QueryController {

    private final OrderInfoRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final InventoryRepository inventoryRepository;
    private final SlotRepository slotRepository;
    private final BatchRepository batchRepository;
    private final ProductRepository productRepository;
    private final MachineRepository machineRepository;

    public QueryController(OrderInfoRepository orderRepository, OrderItemRepository orderItemRepository,
                          InventoryRepository inventoryRepository, SlotRepository slotRepository,
                          BatchRepository batchRepository, ProductRepository productRepository,
                          MachineRepository machineRepository) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.inventoryRepository = inventoryRepository;
        this.slotRepository = slotRepository;
        this.batchRepository = batchRepository;
        this.productRepository = productRepository;
        this.machineRepository = machineRepository;
    }

    @Operation(summary = "我的订单（用户）")
    @PreAuthorize("hasAnyRole('CUSTOMER','ADMIN')")
    @GetMapping("/my-orders")
    public List<Map<String, Object>> myOrders() {
        Long uid = SecurityUtils.currentUserId();
        return orderRepository.findByUserIdOrderByIdDesc(uid).stream().map(o -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("orderNo", o.getOrderNo());
            m.put("status", o.getStatus().name());
            m.put("evidence", o.getEvidence().name());
            m.put("totalAmount", o.getTotalAmount());
            m.put("createdAt", o.getCreatedAt());
            m.put("items", orderItemRepository.findByOrderId(o.getId()).stream().map(oi -> {
                Product p = productRepository.findById(oi.getProductId()).orElse(null);
                return Map.<String, Object>of("product", p == null ? "" : p.getName(),
                        "qty", oi.getQty(), "amount", oi.getAmount(), "refundedQty", oi.getRefundedQty());
            }).toList());
            return m;
        }).toList();
    }

    @Operation(summary = "货柜实时库存（按货道批次）")
    @PreAuthorize("hasAnyRole('OPERATOR','STAFF','FINANCE','ADMIN')")
    @GetMapping("/machines/{machineCode}/inventory")
    public Map<String, Object> inventory(@PathVariable String machineCode) {
        Machine m = machineRepository.findAll().stream().filter(x -> x.getCode().equals(machineCode)).findFirst()
                .orElseThrow(() -> new NoSuchElementException("货柜不存在: " + machineCode));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Inventory inv : inventoryRepository.findByMachineId(m.getId())) {
            if (inv.getQty() == 0) continue;
            Slot slot = slotRepository.findById(inv.getSlotId()).orElse(null);
            Batch b = batchRepository.findById(inv.getBatchId()).orElse(null);
            Product p = b == null ? null : productRepository.findById(b.getProductId()).orElse(null);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("slotCode", slot == null ? null : slot.getCode());
            row.put("batchNo", b == null ? null : b.getBatchNo());
            row.put("product", p == null ? null : p.getName());
            row.put("qty", inv.getQty());
            row.put("expiryDate", b == null ? null : b.getExpiryDate());
            rows.add(row);
        }
        rows.sort(Comparator.comparing(r -> String.valueOf(r.get("slotCode"))));
        return Map.of("machineCode", machineCode, "locked", m.getLocked(), "items", rows);
    }
}
