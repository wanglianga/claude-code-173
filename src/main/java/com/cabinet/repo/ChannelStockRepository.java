package com.cabinet.repo;

import com.cabinet.domain.ChannelStock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChannelStockRepository extends JpaRepository<ChannelStock, Long> {

    List<ChannelStock> findByChannelId(Long channelId);

    List<ChannelStock> findByBatchId(Long batchId);

    Optional<ChannelStock> findByChannelIdAndBatchId(Long channelId, Long batchId);

    /** FEFO：货道内可售（非隔离）且有余量的批次，按到期日升序 */
    @Query("select s from ChannelStock s where s.channel.id = :channelId " +
           "and s.qtyOnHand - s.qtyQuarantined > 0 " +
           "order by s.batch.expiryDate asc, s.batch.id asc")
    List<ChannelStock> findSellableByChannelFefo(@Param("channelId") Long channelId);

    /** 柜机内全部有余额库存 */
    @Query("select s from ChannelStock s join s.channel c where c.cabinet.id = :cabinetId " +
           "and (s.qtyOnHand > 0 or s.qtyQuarantined > 0) order by c.code, s.batch.expiryDate")
    List<ChannelStock> findByCabinetId(@Param("cabinetId") Long cabinetId);

    /** 全链路余量：某批次所有货道在库（含隔离）数量 */
    @Query("select coalesce(sum(s.qtyOnHand),0) from ChannelStock s where s.batch.id = :batchId")
    Integer sumOnHandByBatch(@Param("batchId") Long batchId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ChannelStock s where s.id = :id")
    Optional<ChannelStock> lockById(@Param("id") Long id);
}
