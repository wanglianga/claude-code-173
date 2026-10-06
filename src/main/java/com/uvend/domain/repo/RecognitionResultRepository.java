package com.uvend.domain.repo;

import com.uvend.domain.entity.RecognitionResult;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RecognitionResultRepository extends JpaRepository<RecognitionResult, Long> {
    List<RecognitionResult> findBySessionId(Long sessionId);
}
