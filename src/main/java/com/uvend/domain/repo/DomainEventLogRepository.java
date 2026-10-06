package com.uvend.domain.repo;

import com.uvend.domain.entity.DomainEventLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DomainEventLogRepository extends JpaRepository<DomainEventLog, Long> {
    List<DomainEventLog> findFirst100ByPublishedAtIsNullOrderByIdAsc();
}
