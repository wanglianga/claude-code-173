package com.uvend.service;

import com.uvend.domain.entity.Inventory;
import com.uvend.domain.entity.InventoryLedger;
import com.uvend.domain.enums.LedgerReason;
import com.uvend.domain.repo.InventoryLedgerRepository;
import com.uvend.domain.repo.InventoryRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 库存核心：所有库存变动统一在此 apply（库存表 + 不可变流水 inventory_ledger）。
 * 补货/售卖/放回/下架/报损/盘点调整/放错货道 全部留痕，支持批次轨迹回放。
 */
@Service
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryLedgerRepository ledgerRepository;

    public InventoryService(InventoryRepository inventoryRepository,
                            InventoryLedgerRepository ledgerRepository) {
        this.inventoryRepository = inventoryRepository;
        this.ledgerRepository = ledgerRepository;
    }

    /**
     * 应用一次库存变动。
     * @param change 正=入库，负=出库；变动后余额不可为负
     */
    @Transactional
    public Inventory apply(Long machineId, Long slotId, Long batchId, int change,
                           LedgerReason reason, String refType, Long refId) {
        Inventory inv = inventoryRepository.findBySlotIdAndBatchId(slotId, batchId)
                .orElseGet(() -> {
                    Inventory i = new Inventory();
                    i.setMachineId(machineId);
                    i.setSlotId(slotId);
                    i.setBatchId(batchId);
                    i.setQty(0);
                    return i;
                });
        int newQty = inv.getQty() + change;
        if (newQty < 0) {
            throw new IllegalStateException(
                    "库存不足: machine=" + machineId + " slot=" + slotId + " batch=" + batchId
                            + " current=" + inv.getQty() + " change=" + change);
        }
        inv.setQty(newQty);
        inventoryRepository.save(inv);

        InventoryLedger ledger = new InventoryLedger();
        ledger.setMachineId(machineId);
        ledger.setSlotId(slotId);
        ledger.setBatchId(batchId);
        ledger.setChangeQty(change);
        ledger.setBalanceQty(newQty);
        ledger.setReason(reason);
        ledger.setRefType(refType);
        ledger.setRefId(refId);
        ledgerRepository.save(ledger);
        return inv;
    }

    @Transactional(readOnly = true)
    public int qtyOfSlotBatch(Long slotId, Long batchId) {
        return inventoryRepository.findBySlotIdAndBatchId(slotId, batchId)
                .map(Inventory::getQty).orElse(0);
    }

    @Transactional(readOnly = true)
    public Inventory require(Long slotId, Long batchId) {
        return inventoryRepository.findBySlotIdAndBatchId(slotId, batchId)
                .orElseThrow(() -> new EntityNotFoundException("库存记录不存在 slot=" + slotId + " batch=" + batchId));
    }
}
