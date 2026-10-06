package com.uvend.domain.repo;

import com.uvend.domain.entity.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;


public interface SupplierRepository extends JpaRepository<Supplier, Long> {
}
