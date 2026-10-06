package com.uvend.domain.repo;

import com.uvend.domain.entity.TemperatureLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TemperatureLogRepository extends JpaRepository<TemperatureLog, Long> {
    List<TemperatureLog> findTop200ByMachineIdOrderByOccurredAtDesc(Long machineId);
}
