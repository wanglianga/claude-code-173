package com.cabinet.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 临期/过期定时扫描：生成下架单与告警。
 * cron 可配，测试环境用 "-" 关闭。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExpiryScheduler {

    private final RemovalService removalService;

    @Value("${cabinet.expiry.near-expiry-days:2}")
    private int nearExpiryDays;

    @Scheduled(cron = "${cabinet.scan-cron}")
    public void scan() {
        try {
            int n = removalService.scanExpiry(nearExpiryDays);
            log.info("临期扫描完成，生成下架单 {} 张", n);
        } catch (Exception e) {
            log.error("临期扫描失败", e);
        }
    }
}
