package com.cabinet.repo;

import com.cabinet.domain.RestockOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RestockOrderRepository extends JpaRepository<RestockOrder, Long> {
    Optional<RestockOrder> findByOrderNo(String orderNo);
}
