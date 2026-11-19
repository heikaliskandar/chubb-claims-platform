package com.chubb.claims.claim.api.dto;

import com.chubb.claims.claim.domain.ClaimStatus;
import com.chubb.claims.claim.domain.ClaimType;
import com.chubb.claims.claim.domain.Market;

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
        String currency,
        BigDecimal claimedAmount,
        String assignedOfficerId,
        LocalDate incidentDate,
        Instant createdAt,
        Instant updatedAt) {
}
