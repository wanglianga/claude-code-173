package com.cabinet.repo;

import com.cabinet.domain.SettlementBatch;
import com.cabinet.domain.enums.SettlementStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SettlementBatchRepository extends JpaRepository<SettlementBatch, Long> {
    Optional<SettlementBatch> findBySettlementNo(String settlementNo);

    List<SettlementBatch> findBySupplierIdOrderByPeriodStartDesc(Long supplierId);

    /** 判断某供应商周期内是否已有已确认/已付结算单（避免重复结算） */
    boolean existsBySupplierIdAndStatusInAndPeriodStartAndPeriodEnd(
            Long supplierId, List<SettlementStatus> statuses, LocalDate start, LocalDate end);
}
