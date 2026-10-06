package com.uvend.domain.repo;

import com.uvend.domain.entity.Stocktake;
import org.springframework.data.jpa.repository.JpaRepository;


public interface StocktakeRepository extends JpaRepository<Stocktake, Long> {
}
