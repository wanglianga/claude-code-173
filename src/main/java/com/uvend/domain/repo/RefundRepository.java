package com.uvend.domain.repo;

import com.uvend.domain.entity.Refund;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;

public interface RefundRepository extends JpaRepository<Refund, Long> {
    List<Refund> findByOrderId(Long orderId);
    List<Refund> findByCreatedAtBetween(Instant from, Instant to);
}
