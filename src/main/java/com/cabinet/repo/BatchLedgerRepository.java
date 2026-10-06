package com.cabinet.repo;

import com.cabinet.domain.BatchLedger;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BatchLedgerRepository extends JpaRepository<BatchLedger, Long> {
    List<BatchLedger> findByBatchIdOrderByIdAsc(Long batchId);
}
