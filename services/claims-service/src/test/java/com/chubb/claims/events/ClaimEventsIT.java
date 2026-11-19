package com.chubb.claims.events;

import com.chubb.claims.TestcontainersConfiguration;
import com.chubb.claims.claim.application.ClaimService;
import com.chubb.claims.claim.domain.ClaimDetails;
import com.chubb.claims.claim.domain.ClaimType;
import com.chubb.claims.claim.domain.InvalidClaimStateException;
import com.chubb.claims.claim.domain.Market;
import com.chubb.claims.common.security.CurrentUser;
import com.chubb.claims.common.security.Position;
import com.chubb.claims.common.security.Role;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.ConsumerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "app.security.demo-users.password=test-password")
@Import(TestcontainersConfiguration.class)
class ClaimEventsIT {

    private static final Duration KAFKA_TIMEOUT = Duration.ofSeconds(30);
    private static final CurrentUser CLAIMANT = new CurrentUser("claimant1", Role.CLAIMANT, null);
    private static final CurrentUser OFFICER_1 =
            new CurrentUser("officer1", Role.OFFICER, Position.CLAIMS_OFFICER);
    private static final CurrentUser OFFICER_2 =
            new CurrentUser("officer2", Role.OFFICER, Position.CLAIMS_OFFICER);

    @Autowired
    private ClaimService claimService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private ConsumerFactory<String, String> consumerFactory;

    @Value("${app.kafka.claim-events-topic}")
    private String topic;

    private UUID submitClaim() {
        ClaimDetails details = new ClaimDetails("POL-MY-5531", Market.MY, ClaimType.MOTOR,
                LocalDate.of(2026, 11, 10), "Hit by a motorcycle while parked", new BigDecimal("2750.00"));
        return claimService.submit(CLAIMANT, details, null).claim().id();
    }

    private List<Map<String, Object>> outboxRowsFor(UUID claimId) {
        return jdbc.queryForList(
                "SELECT event_type, payload, published_at FROM outbox_event WHERE aggregate_id = ? ORDER BY created_at",
                claimId);
    }

    @Test
    void submittingAClaimWritesTheClaimAndItsOutboxEventTogether() {
        UUID claimId = submitClaim();

        assertThat(jdbc.queryForObject("SELECT status FROM claim WHERE id = ?", String.class, claimId))
                .isEqualTo("SUBMITTED");
        List<Map<String, Object>> rows = outboxRowsFor(claimId);
        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.get("event_type")).isEqualTo("CLAIM_SUBMITTED");
            JsonNode payload = jsonMapper.readTree((String) row.get("payload"));
            assertThat(payload.get("claim").get("id").asString()).isEqualTo(claimId.toString());
            assertThat(payload.get("claim").get("currency").asString()).isEqualTo("MYR");
            assertThat(payload.get("claim").has("policyNumber")).isFalse();
            assertThat(payload.get("claim").has("incidentDescription")).isFalse();
        });
    }

    @Test
    void aRejectedCommandWritesNoOutboxEvent() {
        UUID claimId = submitClaim();
        claimService.assign(claimId, OFFICER_1);

        assertThatThrownBy(() -> claimService.assign(claimId, OFFICER_2))
                .isInstanceOf(InvalidClaimStateException.class);

        assertThat(outboxRowsFor(claimId)).extracting(row -> row.get("event_type"))
                .containsExactly("CLAIM_SUBMITTED", "CLAIM_ASSIGNED");
    }

    @Test
    void publishesEventsToKafkaKeyedByClaimIdInVersionOrder() {
        UUID claimId = submitClaim();
        claimService.assign(claimId, OFFICER_1);
        claimService.startReview(claimId, OFFICER_1);

        List<JsonNode> events = consumeEventsFor(claimId, 3);

        assertThat(events).extracting(event -> event.get("eventType").asString())
                .containsExactly("CLAIM_SUBMITTED", "CLAIM_ASSIGNED", "CLAIM_REVIEW_STARTED");
        assertThat(events).extracting(event -> event.get("claim").get("version").asLong())
                .containsExactly(0L, 1L, 2L);
        assertThat(outboxRowsFor(claimId)).allSatisfy(row -> assertThat(row.get("published_at")).isNotNull());
    }

    private List<JsonNode> consumeEventsFor(UUID claimId, int expected) {
        Properties overrides = new Properties();
        overrides.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        List<JsonNode> events = new ArrayList<>();
        try (Consumer<String, String> consumer =
                     consumerFactory.createConsumer("claim-events-it-" + claimId, null, null, overrides)) {
            consumer.subscribe(List.of(topic));
            Instant deadline = Instant.now().plus(KAFKA_TIMEOUT);
            while (events.size() < expected && Instant.now().isBefore(deadline)) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    if (record.key().equals(claimId.toString())) {
                        events.add(jsonMapper.readTree(record.value()));
                    }
                }
            }
        }
        return events;
    }
}
