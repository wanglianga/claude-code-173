package com.uvend.service;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

/** 业务单号生成：前缀 + yyMMddHHmmss + 两位进程内序列。 */
@Component
public class Numbers {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyMMddHHmmss");
    private final AtomicInteger seq = new AtomicInteger(0);

    public String next(String prefix) {
        int s = seq.updateAndGet(v -> (v + 1) % 100);
        return prefix + LocalDateTime.now().format(FMT) + String.format("%02d", s);
    }
}
