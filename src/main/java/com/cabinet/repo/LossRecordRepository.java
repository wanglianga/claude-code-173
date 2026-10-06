package com.cabinet.repo;

import com.cabinet.domain.LossRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LossRecordRepository extends JpaRepository<LossRecord, Long> {
    List<LossRecord> findByBatchIdOrderByIdAsc(Long batchId);
    List<LossRecord> findByCabinetId(Long cabinetId);
}
