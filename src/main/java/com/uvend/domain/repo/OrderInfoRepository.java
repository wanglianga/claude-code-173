package com.uvend.domain.repo;

import com.uvend.domain.entity.OrderInfo;
import com.uvend.domain.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OrderInfoRepository extends JpaRepository<OrderInfo, Long> {
    List<OrderInfo> findByUserIdOrderByIdDesc(Long userId);
    Optional<OrderInfo> findBySessionId(Long sessionId);
    List<OrderInfo> findByMachineId(Long machineId);

    /** 时间区间内、已确认（含退款）订单，供财务结算。 */
    @Query("select o from OrderInfo o where o.createdAt >= :from and o.createdAt < :to "
            + "and o.status in :statuses")
    List<OrderInfo> findInPeriodWithStatus(@Param("from") Instant from, @Param("to") Instant to,
                                           @Param("statuses") List<OrderStatus> statuses);
}
