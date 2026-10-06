package com.uvend;

import com.uvend.domain.entity.DomainEventLog;
import com.uvend.domain.repo.DomainEventLogRepository;
import com.uvend.event.EventPublisher;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 验证 Kafka 事件通路（uvend.eventbus=kafka）：
 * 补货事务提交后事件经真实 Kafka broker（KRaft 嵌入式）投递到 uvend.restock，
 * 且 outbox(domain_event_log) 中对应记录 published_at 被标记（afterCommit + relay 定时兜底）。
 * 注：@EmbeddedKafka 通过 bootstrapServersProperty 把 spring.kafka.bootstrap-servers 指向嵌入式 broker。
 */
@SpringBootTest
@AutoConfigureMockMvc
@EmbeddedKafka(partitions = 1, topics = {"uvend.restock"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
class KafkaEventBusTest {

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("uvend.eventbus", () -> "kafka");
        registry.add("uvend.kafka.relay-ms", () -> "1000");
    }

    @Autowired MockMvc mockMvc;
    @Autowired DomainEventLogRepository eventLogRepository;
    @Autowired EmbeddedKafkaBroker embeddedKafka;
    @Autowired EventPublisher eventPublisher;

    @Test
    void restock_event_delivered_via_kafka_and_outbox_marked() throws Exception {
        assertTrue(eventPublisher.getClass().getSimpleName().contains("Kafka"),
                "应启用 KafkaEventPublisher，实际: " + eventPublisher.getClass());

        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(
                "kafka-verify-" + System.nanoTime(), "true", embeddedKafka);
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps,
                new StringDeserializer(), new StringDeserializer());
        embeddedKafka.consumeFromAnEmbeddedTopic(consumer, "uvend.restock");

        String batch = "B-MILK-" + LocalDate.now().minusDays(1);
        String body = """
            {"machineCode":"VM-C001","photoUrl":"kafka.jpg","note":"kafka验证",
             "items":[{"batchNo":"%s","slotCode":"A01","qty":1,"tempRequirement":"CHILLED"}]}
            """.formatted(batch);

        MvcResult r = mockMvc.perform(post("/api/staff/restock")
                        .with(httpBasic("staff", "password123"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn();
        assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());

        String received = null;
        long deadline = System.currentTimeMillis() + 30000;
        while (received == null && System.currentTimeMillis() < deadline) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
            for (ConsumerRecord<String, String> rec : records) {
                if ("VM-C001".equals(rec.key())
                        && rec.value().contains("replenishNo")
                        && rec.value().contains("\"state\":\"PLACED\"")) {
                    received = rec.value();
                }
            }
        }
        consumer.close();
        assertNotNull(received, "补货事件应通过真实 Kafka 投递到 uvend.restock");
        assertTrue(received.contains("\"batchNo\":\"" + batch + "\""), received);

        deadline = System.currentTimeMillis() + 15000;
        boolean marked = false;
        while (!marked && System.currentTimeMillis() < deadline) {
            marked = eventLogRepository.findAll().stream()
                    .filter(e -> "uvend.restock".equals(e.getTopic()) && "Restocked".equals(e.getEventType()))
                    .map(DomainEventLog::getPublishedAt).anyMatch(p -> p != null);
            if (!marked) Thread.sleep(500);
        }
        assertTrue(marked, "outbox 事件应标记 published_at（afterCommit 或 relay 兜底）");
    }
}
