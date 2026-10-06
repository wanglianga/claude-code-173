package com.uvend.domain.repo;

import com.uvend.domain.entity.QualityHold;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface QualityHoldRepository extends JpaRepository<QualityHold, Long> {
    List<QualityHold> findByBatchIdAndStatus(Long batchId, com.uvend.domain.enums.HoldStatus status);
    List<QualityHold> findByMachineIdAndStatus(Long machineId, com.uvend.domain.enums.HoldStatus status);
}
