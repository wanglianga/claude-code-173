package com.cabinet.service;

import com.cabinet.domain.BatchLedger;
import com.cabinet.domain.Cabinet;
import com.cabinet.domain.ProductBatch;
import com.cabinet.domain.enums.BatchStatus;
import com.cabinet.domain.enums.LedgerType;
import com.cabinet.repo.BatchLedgerRepository;
import com.cabinet.repo.ChannelStockRepository;
import com.cabinet.repo.ProductBatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 批次台账服务：补货入/售卖出/下架出/报损出/盘点调整/退款回库/结算。
 * 每笔记账后回写批次在库余量；余量归零且无在途时置为售罄（结算状态由结算单驱动）。
 */
@Service
@RequiredArgsConstructor
public class BatchLedgerService {

    private final BatchLedgerRepository ledgerRepository;
    private final ChannelStockRepository stockRepository;
    private final ProductBatchRepository batchRepository;

    @Transactional
    public BatchLedger record(ProductBatch batch, Cabinet cabinet, LedgerType type,
                              int changeQty, BigDecimal amount,
                              String bizType, Long bizId, String remark) {
        int balance = stockRepository.sumOnHandByBatch(batch.getId());
        BatchLedger ledger = BatchLedger.builder()
                .batch(batch)
                .cabinet(cabinet)
                .ledgerType(type)
                .changeQty(changeQty)
                .balanceQty(balance)
                .amount(amount == null ? BigDecimal.ZERO : amount)
                .bizType(bizType)
                .bizId(bizId)
                .remark(remark)
                .build();
        ledgerRepository.save(ledger);

        // 余量归零且非下架/结算场景 -> 售罄；下架场景由 RemovalService 置 REMOVED
        if (balance <= 0 && batch.getStatus() == BatchStatus.IN_STOCK
                && type != LedgerType.REMOVAL_OUT && type != LedgerType.LOSS_OUT) {
            batch.setStatus(BatchStatus.SOLD_OUT);
            batchRepository.save(batch);
        }
        return ledger;
    }
}
