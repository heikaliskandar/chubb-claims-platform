package com.chubb.insights.projection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Component
public class ClaimEventListener {

    private static final Logger log = LoggerFactory.getLogger(ClaimEventListener.class);

    private final JsonMapper jsonMapper;
    private final ProcessedEventRepository processedEvents;
    private final ClaimProjectionRepository projections;
    private final ClaimantNotifier notifier;

    public ClaimEventListener(JsonMapper jsonMapper, ProcessedEventRepository processedEvents,
                              ClaimProjectionRepository projections, ClaimantNotifier notifier) {
        this.jsonMapper = jsonMapper;
        this.processedEvents = processedEvents;
        this.projections = projections;
        this.notifier = notifier;
    }

    @KafkaListener(topics = "${app.kafka.claim-events-topic}")
    @Transactional
    public void onClaimEvent(String payload) {
        ClaimEvent event = jsonMapper.readValue(payload, ClaimEvent.class);
        if (!processedEvents.markProcessed(event.eventId())) {
            log.info("Skipped duplicate event {}", event.eventId());
            return;
        }
        if (!projections.upsertIfNewer(event.claim(), event.occurredAt())) {
            log.info("Kept newer state of claim {}; event {} carried version {}",
                    event.claim().claimNumber(), event.eventId(), event.claim().version());
        }
        notifier.notifyClaimant(event);
    }
}
