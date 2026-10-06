package com.uvend.domain.repo;

import com.uvend.domain.entity.ReplenishmentItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ReplenishmentItemRepository extends JpaRepository<ReplenishmentItem, Long> {
    List<ReplenishmentItem> findByReplenishmentId(Long replenishmentId);
    List<ReplenishmentItem> findByBatchId(Long batchId);
}
