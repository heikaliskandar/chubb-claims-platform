package com.claimsplatform.insights.projection;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ClaimEvent(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        String actor,
        ClaimSnapshot claim) {

    public record ClaimSnapshot(
            UUID id,
            long version,
            String claimNumber,
            String claimantId,
            String market,
            String claimType,
            String status,
            String currency,
            BigDecimal claimedAmount,
            BigDecimal reserveAmount,
            String assignedOfficerId,
            Instant submittedAt,
            Instant decidedAt) {
    }
}
