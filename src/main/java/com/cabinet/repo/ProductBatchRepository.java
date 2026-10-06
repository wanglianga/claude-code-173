package com.cabinet.repo;

import com.cabinet.domain.ProductBatch;
import com.cabinet.domain.enums.BatchStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ProductBatchRepository extends JpaRepository<ProductBatch, Long> {
    Optional<ProductBatch> findByBatchNo(String batchNo);

    /** 查在库且到期日 <= threshold 的批次（临期/过期扫描） */
    List<ProductBatch> findByStatusAndExpiryDateLessThanEqual(BatchStatus status, LocalDate threshold);
}
