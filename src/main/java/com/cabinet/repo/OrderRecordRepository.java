package com.cabinet.repo;

import com.cabinet.domain.OrderRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderRecordRepository extends JpaRepository<OrderRecord, Long> {
    Optional<OrderRecord> findByOrderNo(String orderNo);
    Optional<OrderRecord> findBySessionId(Long sessionId);
    List<OrderRecord> findByCabinetId(Long cabinetId);
    List<OrderRecord> findByCabinetIdIn(List<Long> cabinetIds);
}
