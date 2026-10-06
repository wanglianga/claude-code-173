package com.uvend.domain.repo;

import com.uvend.domain.entity.EventLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface EventLogRepository extends JpaRepository<EventLog, Long> {
    List<EventLog> findBySessionIdOrderBySeqAsc(Long sessionId);
    long countBySessionId(Long sessionId);
}
