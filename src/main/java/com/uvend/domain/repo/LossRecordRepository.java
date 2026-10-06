package com.uvend.domain.repo;

import com.uvend.domain.entity.LossRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface LossRecordRepository extends JpaRepository<LossRecord, Long> {
    List<LossRecord> findByBatchId(Long batchId);
    List<LossRecord> findByMachineId(Long machineId);
    List<LossRecord> findByCreatedAtBetween(Instant from, Instant to);
}
