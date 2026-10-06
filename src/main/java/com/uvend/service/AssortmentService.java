package com.uvend.service;

import com.uvend.domain.entity.AssortmentAdjustment;
import com.uvend.domain.entity.Machine;
import com.uvend.domain.entity.Product;
import com.uvend.domain.entity.Slot;
import com.uvend.domain.repo.*;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/** 运营按点位调整品类：变更货道应陈列商品，留痕 assortment_adjustment。 */
@Service
public class AssortmentService {

    private final MachineRepository machineRepository;
    private final SlotRepository slotRepository;
    private final ProductRepository productRepository;
    private final AssortmentAdjustmentRepository adjustmentRepository;

    public AssortmentService(MachineRepository machineRepository, SlotRepository slotRepository,
                             ProductRepository productRepository,
                             AssortmentAdjustmentRepository adjustmentRepository) {
        this.machineRepository = machineRepository;
        this.slotRepository = slotRepository;
        this.productRepository = productRepository;
        this.adjustmentRepository = adjustmentRepository;
    }

    @Transactional
    public Map<String, Object> adjust(Long operatorId, String machineCode, String slotCode,
                                      String newProductSku, String reason) {
        Machine machine = machineRepository.findAll().stream().filter(m -> m.getCode().equals(machineCode))
                .findFirst().orElseThrow(() -> new EntityNotFoundException("货柜不存在: " + machineCode));
        Slot slot = slotRepository.findByMachineIdAndCode(machine.getId(), slotCode)
                .orElseThrow(() -> new EntityNotFoundException("货道不存在: " + slotCode));

        Long oldProductId = slot.getProductId();
        Long newProductId = null;
        String newName = null;
        if (newProductSku != null && !newProductSku.isBlank()) {
            Product p = productRepository.findBySku(newProductSku)
                    .orElseThrow(() -> new EntityNotFoundException("商品 SKU 不存在: " + newProductSku));
            if (!p.getActive()) throw new IllegalArgumentException("商品已下架，不能设定陈列");
            if (p.getTempRequirement() != machine.getMachineType()
                    && p.getTempRequirement() != com.uvend.domain.enums.TempClass.AMBIENT) {
                throw new IllegalArgumentException("商品温控要求与货柜类型不符，禁止调整到该货道");
            }
            newProductId = p.getId();
            newName = p.getName();
        }

        AssortmentAdjustment adj = new AssortmentAdjustment();
        adj.setMachineId(machine.getId());
        adj.setSlotId(slot.getId());
        adj.setOldProductId(oldProductId);
        adj.setNewProductId(newProductId);
        adj.setReason(reason);
        adj.setOperatorId(operatorId);
        adjustmentRepository.save(adj);

        slot.setProductId(newProductId);
        slotRepository.save(slot);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("machineCode", machineCode);
        m.put("slotCode", slotCode);
        m.put("newProduct", newName);
        m.put("reason", reason == null ? "" : reason);
        return m;
    }
}
