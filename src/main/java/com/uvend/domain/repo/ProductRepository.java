package com.uvend.domain.repo;

import com.uvend.domain.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findBySupplierId(Long supplierId);
    List<Product> findByActiveTrue();
    Optional<Product> findBySku(String sku);
}
