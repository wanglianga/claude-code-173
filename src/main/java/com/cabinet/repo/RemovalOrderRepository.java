package com.cabinet.repo;

import com.cabinet.domain.RemovalOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RemovalOrderRepository extends JpaRepository<RemovalOrder, Long> {
    Optional<RemovalOrder> findByOrderNo(String orderNo);
}
