package com.uvend.domain.repo;

import com.uvend.domain.entity.Machine;
import org.springframework.data.jpa.repository.JpaRepository;


public interface MachineRepository extends JpaRepository<Machine, Long> {
}
