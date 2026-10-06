package com.uvend.domain.repo;

import com.uvend.domain.entity.SettlementItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SettlementItemRepository extends JpaRepository<SettlementItem, Long> {
    List<SettlementItem> findBySettlementId(Long settlementId);
}
