package com.chubb.claims.outbox;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.UUID;

@Component
public class OutboxWriter {

    private final OutboxRepository outbox;
    private final JsonMapper jsonMapper;

    public OutboxWriter(OutboxRepository outbox, JsonMapper jsonMapper) {
        this.outbox = outbox;
        this.jsonMapper = jsonMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void append(UUID eventId, UUID aggregateId, String eventType, Object event, Instant createdAt) {
        String payload = jsonMapper.writeValueAsString(event);
        outbox.save(new OutboxEvent(eventId, aggregateId, eventType, payload, createdAt));
    }
}
