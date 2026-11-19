package com.chubb.insights.projection;

import com.chubb.insights.TestcontainersConfiguration;
import com.chubb.insights.projection.ClaimEvent.ClaimSnapshot;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(properties = "app.security.demo-users.password=test-password")
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class ClaimEventListenerIT {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final Instant SUBMITTED_AT = Instant.parse("2026-11-18T09:00:00Z");

    @Autowired
    private KafkaTemplate<String, String> kafka;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private ConsumerFactory<String, String> consumerFactory;

    @Value("${app.kafka.claim-events-topic}")
    private String topic;

    private static ClaimEvent event(UUID claimId, String claimNumber, long version, String eventType,
                                    String status, String reserve) {
        ClaimSnapshot snapshot = new ClaimSnapshot(claimId, version, claimNumber, "claimant1", "SG", "MOTOR",
                status, "SGD", new BigDecimal("5000.00"), new BigDecimal(reserve), "officer1", SUBMITTED_AT, null);
        return new ClaimEvent(UUID.randomUUID(), eventType, SUBMITTED_AT.plusSeconds(version), "officer1", snapshot);
    }

    private void publish(ClaimEvent event) {
        kafka.send(topic, event.claim().id().toString(), jsonMapper.writeValueAsString(event)).join();
    }

    private long projectedVersion(UUID claimId) {
        return jdbc.sql("SELECT version FROM claim_projection WHERE claim_id = :id")
                .param("id", claimId).query(Long.class).optional().orElse(-1L);
    }

    @Test
    void appliesEachEventOnceEvenWhenKafkaRedeliversIt(CapturedOutput output) {
        UUID claimId = UUID.randomUUID();
        String claimNumber = "CLM-SG-2026-" + claimId.toString().substring(0, 6);
        ClaimEvent infoRequested = event(claimId, claimNumber, 2, "INFORMATION_REQUESTED", "INFO_REQUESTED", "0.00");

        publish(infoRequested);
        publish(infoRequested);
        publish(event(claimId, claimNumber, 3, "INFORMATION_PROVIDED", "UNDER_REVIEW", "0.00"));

        await().atMost(TIMEOUT).until(() -> projectedVersion(claimId) == 3);
        assertThat(output.getOut().lines()
                .filter(line -> line.contains("Notification to claimant1 about claim " + claimNumber)))
                .hasSize(1);
        assertThat(output.getOut()).contains("Skipped duplicate event " + infoRequested.eventId());
    }

    @Test
    void anOlderSnapshotNeverOverwritesANewerOne() {
        UUID claimId = UUID.randomUUID();
        ClaimEvent newer = event(claimId, "CLM-SG-2026-900001", 5, "CLAIM_ASSESSED", "UNDER_REVIEW", "4200.00");
        ClaimEvent older = event(claimId, "CLM-SG-2026-900001", 4, "CLAIM_REVIEW_STARTED", "UNDER_REVIEW", "0.00");

        publish(newer);
        publish(older);

        await().atMost(TIMEOUT).until(() -> jdbc.sql("SELECT count(*) FROM processed_event WHERE event_id = :id")
                .param("id", older.eventId()).query(Long.class).single() == 1);
        assertThat(projectedVersion(claimId)).isEqualTo(5);
        assertThat(jdbc.sql("SELECT reserve_amount FROM claim_projection WHERE claim_id = :id")
                .param("id", claimId).query(BigDecimal.class).single()).isEqualByComparingTo("4200.00");
    }

    @Test
    void sendsAnUnreadableEventToTheDeadLetterTopic() {
        String key = UUID.randomUUID().toString();
        kafka.send(topic, key, "{not json").join();

        Properties overrides = new Properties();
        overrides.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (Consumer<String, String> consumer =
                     consumerFactory.createConsumer("dlt-it-" + key, null, null, overrides)) {
            consumer.subscribe(List.of(topic + ".dlt"));
            await().atMost(TIMEOUT).until(() -> {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    if (key.equals(record.key())) {
                        return record.value().equals("{not json");
                    }
                }
                return false;
            });
        }
    }
}
