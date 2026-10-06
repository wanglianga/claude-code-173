package com.cabinet.repo;

import com.cabinet.domain.Cabinet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CabinetRepository extends JpaRepository<Cabinet, Long> {
    Optional<Cabinet> findByCabinetNo(String cabinetNo);
}
