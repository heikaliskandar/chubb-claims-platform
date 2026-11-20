package com.claimsplatform.claims.claim.api.dto;

import com.claimsplatform.claims.claim.domain.ClaimStatus;
import com.claimsplatform.claims.claim.domain.ClaimType;
import com.claimsplatform.claims.claim.domain.Market;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ClaimSummaryResponse(
        UUID id,
        String claimNumber,
        Market market,
        ClaimType claimType,
        ClaimStatus status,
        ClaimStatus.Party waitingOn,
        String currency,
        BigDecimal claimedAmount,
        String assignedOfficerId,
        LocalDate incidentDate,
        Instant createdAt,
        Instant updatedAt) {
}
