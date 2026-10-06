package com.uvend.domain.repo;

import com.uvend.domain.entity.Batch;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BatchRepository extends JpaRepository<Batch, Long> {
    List<Batch> findBySupplierId(Long supplierId);
    List<Batch> findByProductId(Long productId);
}
