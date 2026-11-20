package com.claimsplatform.claims.events;

import com.claimsplatform.claims.claim.domain.Claim;
import com.claimsplatform.claims.claim.infrastructure.ClaimRepository;
import com.claimsplatform.claims.outbox.OutboxWriter;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Component
public class ClaimEventRecorder {

    private final ClaimRepository claims;
    private final OutboxWriter outbox;
    private final Clock clock;

    public ClaimEventRecorder(ClaimRepository claims, OutboxWriter outbox, Clock clock) {
        this.claims = claims;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(ClaimEventType eventType, Claim claim, String actor) {
        // Flush first so @Version is incremented and the snapshot carries the version this change commits with.
        claims.flush();
        ClaimEvent event = ClaimEvent.of(eventType, claim, actor, clock.instant());
        outbox.append(event.eventId(), claim.getId(), eventType.name(), event, event.occurredAt());
    }
}
