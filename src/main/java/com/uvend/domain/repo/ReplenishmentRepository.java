package com.uvend.domain.repo;

import com.uvend.domain.entity.Replenishment;
import org.springframework.data.jpa.repository.JpaRepository;


public interface ReplenishmentRepository extends JpaRepository<Replenishment, Long> {
}
