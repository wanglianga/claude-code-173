package com.uvend.domain.repo;

import com.uvend.domain.entity.Complaint;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ComplaintRepository extends JpaRepository<Complaint, Long> {
    List<Complaint> findByOrderId(Long orderId);
    List<Complaint> findByUserIdOrderByIdDesc(Long userId);
}
