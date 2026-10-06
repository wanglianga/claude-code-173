package com.cabinet;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/** 测试环境用 Mock KafkaTemplate（raw 类型以匹配各参数化注入点），避免连接真实 Broker */
@TestConfiguration
@SuppressWarnings({"unchecked", "rawtypes"})
public class TestKafkaConfig {

    @Bean
    @Primary
    public KafkaTemplate kafkaTemplate() {
        KafkaTemplate template = mock(KafkaTemplate.class);
        lenient().when(template.send(anyString(), any(), any()))
                .thenReturn(CompletableFuture.<SendResult>completedFuture(null));
        return template;
    }
}
