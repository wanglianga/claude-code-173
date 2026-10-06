package com.uvend.domain.repo;

import com.uvend.domain.entity.AssortmentAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AssortmentAdjustmentRepository extends JpaRepository<AssortmentAdjustment, Long> {
    List<AssortmentAdjustment> findByMachineIdOrderByIdDesc(Long machineId);
}
