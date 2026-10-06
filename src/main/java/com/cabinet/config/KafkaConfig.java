package com.cabinet.config;

import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Kafka 主题与消费错误处理：
 * - cabinet.device.event：柜机上报（开门/关门/识别/温度/心跳/卡货）
 * - cabinet.biz.event：服务产生的业务事件（结算/告警/下架/报损/结算单）
 * 毒消息进入 DLT，不阻塞分区。
 */
@EnableKafka
@Configuration
public class KafkaConfig {

    public static final String TOPIC_DEVICE = "cabinet.device.event";
    public static final String TOPIC_BIZ = "cabinet.biz.event";

    @Bean
    public org.apache.kafka.clients.admin.NewTopic deviceTopic() {
        return TopicBuilder.name(TOPIC_DEVICE).partitions(3).replicas(1).build();
    }

    @Bean
    public org.apache.kafka.clients.admin.NewTopic bizTopic() {
        return TopicBuilder.name(TOPIC_BIZ).partitions(3).replicas(1).build();
    }

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> template) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                template,
                (rec, ex) -> new TopicPartition(rec.topic() + ".DLT", rec.partition()));
        // 重试 2 次、间隔 1s，仍失败投递到 DLT
        return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 2));
    }
}
