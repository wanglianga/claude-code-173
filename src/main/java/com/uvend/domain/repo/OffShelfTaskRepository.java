package com.uvend.domain.repo;

import com.uvend.domain.entity.OffShelfTask;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface OffShelfTaskRepository extends JpaRepository<OffShelfTask, Long> {
    List<OffShelfTask> findByStatus(com.uvend.domain.enums.OffShelfStatus status);
    List<OffShelfTask> findByBatchId(Long batchId);
    List<OffShelfTask> findByMachineId(Long machineId);
}
