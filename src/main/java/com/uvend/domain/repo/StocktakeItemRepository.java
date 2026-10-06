package com.uvend.domain.repo;

import com.uvend.domain.entity.StocktakeItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface StocktakeItemRepository extends JpaRepository<StocktakeItem, Long> {
    List<StocktakeItem> findByStocktakeId(Long stocktakeId);
}
