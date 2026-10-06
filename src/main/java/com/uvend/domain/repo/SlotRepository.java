package com.uvend.domain.repo;

import com.uvend.domain.entity.Slot;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SlotRepository extends JpaRepository<Slot, Long> {
    List<Slot> findByMachineId(Long machineId);
    Optional<Slot> findByMachineIdAndCode(Long machineId, String code);
}
