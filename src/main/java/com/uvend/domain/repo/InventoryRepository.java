package com.uvend.domain.repo;

import com.uvend.domain.entity.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {
    Optional<Inventory> findBySlotIdAndBatchId(Long slotId, Long batchId);
    List<Inventory> findByMachineId(Long machineId);
    List<Inventory> findByBatchId(Long batchId);
    List<Inventory> findBySlotId(Long slotId);
}
