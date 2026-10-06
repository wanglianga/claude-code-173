package com.cabinet.repo;

import com.cabinet.domain.SettlementItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SettlementItemRepository extends JpaRepository<SettlementItem, Long> {
    List<SettlementItem> findBySettlementId(Long settlementId);
    List<SettlementItem> findByBatchId(Long batchId);
}
