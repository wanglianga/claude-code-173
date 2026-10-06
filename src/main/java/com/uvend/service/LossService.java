package com.uvend.service;

import com.uvend.domain.entity.*;
import com.uvend.domain.enums.*;
import com.uvend.domain.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/** 报损登记：结算口径统一在此计算成本与承担方。 */
@Service
public class LossService {

    private final LossRecordRepository lossRepository;
    private final BatchRepository batchRepository;
    private final ProductRepository productRepository;
    private final SupplierRepository supplierRepository;
    private final Numbers numbers;

    public LossService(LossRecordRepository lossRepository, BatchRepository batchRepository,
                       ProductRepository productRepository, SupplierRepository supplierRepository,
                       Numbers numbers) {
        this.lossRepository = lossRepository;
        this.batchRepository = batchRepository;
        this.productRepository = productRepository;
        this.supplierRepository = supplierRepository;
        this.numbers = numbers;
    }

    @Transactional
    public LossRecord record(Long batchId, Long machineId, Long slotId, int qty, LossReason reason,
                             String refType, Long refId) {
        if (qty <= 0) throw new IllegalArgumentException("报损数量必须为正");
        Batch batch = batchRepository.findById(batchId).orElseThrow();
        Product product = productRepository.findById(batch.getProductId()).orElseThrow();
        Supplier supplier = supplierRepository.findById(batch.getSupplierId()).orElseThrow();

        LossRecord loss = new LossRecord();
        loss.setLossNo(numbers.next("LS"));
        loss.setBatchId(batchId);
        loss.setMachineId(machineId);
        loss.setSlotId(slotId);
        loss.setQty(qty);
        loss.setReason(reason);
        loss.setRefType(refType);
        loss.setRefId(refId);
        loss.setCostAmount(product.getCostPrice().multiply(BigDecimal.valueOf(qty)));
        // 供应商签约承担临期/温控损耗则记 SUPPLIER（结算扣款），否则运营自担
        loss.setBearer(Boolean.TRUE.equals(supplier.getBearLoss()) ? Bearer.SUPPLIER : Bearer.OPERATOR);
        return lossRepository.save(loss);
    }
}
