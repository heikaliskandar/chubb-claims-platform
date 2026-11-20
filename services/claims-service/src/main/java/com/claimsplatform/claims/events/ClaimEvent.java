package com.claimsplatform.claims.events;

import com.claimsplatform.claims.claim.domain.Claim;
import com.claimsplatform.claims.claim.domain.ClaimStatus;
import com.claimsplatform.claims.claim.domain.ClaimType;
import com.claimsplatform.claims.claim.domain.Market;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ClaimEvent(
        UUID eventId,
        ClaimEventType eventType,
        Instant occurredAt,
        String actor,
        ClaimSnapshot claim) {

    public record ClaimSnapshot(
            UUID id,
            long version,
            String claimNumber,
            String claimantId,
            Market market,
            ClaimType claimType,
            ClaimStatus status,
            String currency,
            BigDecimal claimedAmount,
            BigDecimal reserveAmount,
            String assignedOfficerId,
            Instant submittedAt,
            Instant decidedAt) {
    }

    public static ClaimEvent of(ClaimEventType eventType, Claim claim, String actor, Instant occurredAt) {
        ClaimSnapshot snapshot = new ClaimSnapshot(
                claim.getId(),
                claim.getVersion(),
                claim.getClaimNumber(),
                claim.getClaimantId(),
                claim.getMarket(),
                claim.getClaimType(),
                claim.getStatus(),
                claim.getCurrency(),
                claim.getClaimedAmount(),
                claim.getReserveAmount(),
                claim.getAssignedOfficerId(),
                claim.getCreatedAt(),
                claim.getDecision() == null ? null : claim.getDecision().decidedAt());
        return new ClaimEvent(UUID.randomUUID(), eventType, occurredAt, actor, snapshot);
    }
}
