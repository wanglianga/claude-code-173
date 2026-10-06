package com.cabinet.repo;

import com.cabinet.domain.StocktakeItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StocktakeItemRepository extends JpaRepository<StocktakeItem, Long> {
    List<StocktakeItem> findByStocktakeId(Long stocktakeId);
}
