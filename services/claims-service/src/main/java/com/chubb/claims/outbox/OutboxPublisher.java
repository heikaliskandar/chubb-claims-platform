package com.chubb.claims.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final int BATCH_SIZE = 100;
    private static final long SEND_TIMEOUT_SECONDS = 10;

    private final OutboxRepository outbox;
    private final KafkaTemplate<String, String> kafka;
    private final Clock clock;
    private final String topic;

    public OutboxPublisher(OutboxRepository outbox, KafkaTemplate<String, String> kafka, Clock clock,
                           @Value("${app.kafka.claim-events-topic}") String topic) {
        this.outbox = outbox;
        this.kafka = kafka;
        this.clock = clock;
        this.topic = topic;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms}")
    @Transactional
    public void publishPending() {
        List<OutboxEvent> batch = outbox.lockUnpublished(BATCH_SIZE);
        if (batch.isEmpty()) {
            return;
        }
        List<CompletableFuture<SendResult<String, String>>> sends = batch.stream()
                .map(event -> kafka.send(topic, event.getAggregateId().toString(), event.getPayload()))
                .toList();
        awaitAcknowledgements(sends);
        batch.forEach(event -> event.markPublished(clock.instant()));
        log.debug("Published {} outbox events", batch.size());
    }

    private static void awaitAcknowledgements(List<CompletableFuture<SendResult<String, String>>> sends) {
        try {
            CompletableFuture.allOf(sends.toArray(CompletableFuture[]::new))
                    .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new OutboxPublishException(exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new OutboxPublishException(exception);
        }
    }

    static class OutboxPublishException extends RuntimeException {

        OutboxPublishException(Throwable cause) {
            super("Kafka did not acknowledge the outbox batch; it will be retried", cause);
        }
    }
}
