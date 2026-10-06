package com.uvend.domain.repo;

import com.uvend.domain.entity.InventoryLedger;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface InventoryLedgerRepository extends JpaRepository<InventoryLedger, Long> {
    List<InventoryLedger> findByBatchIdOrderByIdAsc(Long batchId);
    List<InventoryLedger> findByRefTypeAndRefIdOrderByIdAsc(String refType, Long refId);
    List<InventoryLedger> findByMachineIdOrderByIdAsc(Long machineId);
    List<InventoryLedger> findByMachineIdAndCreatedAtBetween(Long machineId, Instant from, Instant to);
}
