package com.uvend.domain.repo;

import com.uvend.domain.entity.TemperatureAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface TemperatureAlertRepository extends JpaRepository<TemperatureAlert, Long> {
    Optional<TemperatureAlert> findFirstByMachineIdAndStatusOrderByIdDesc(Long machineId,
            com.uvend.domain.enums.AlertStatus status);
    List<TemperatureAlert> findByMachineIdOrderByIdDesc(Long machineId);
}
