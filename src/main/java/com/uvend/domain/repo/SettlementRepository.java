package com.uvend.domain.repo;

import com.uvend.domain.entity.Settlement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {
    List<Settlement> findBySupplierIdOrderByIdDesc(Long supplierId);
    boolean existsBySupplierIdAndPeriodStartAndPeriodEnd(Long supplierId,
                                                         java.time.LocalDate start, java.time.LocalDate end);
}
