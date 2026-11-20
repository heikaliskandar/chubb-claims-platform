package com.claimsplatform.claims.claim.api.dto;

import com.claimsplatform.claims.claim.domain.ClaimType;
import com.claimsplatform.claims.claim.domain.Market;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ApprovalResponse(
        UUID claimId,
        String claimNumber,
        Market market,
        ClaimType claimType,
        String currency,
        BigDecimal claimedAmount,
        BigDecimal recommendedAmount,
        String reason,
        String referredBy,
        Instant referredAt,
        Status status,
        String reviewedBy,
        Instant reviewedAt,
        boolean canReview) {

    public enum Status { PENDING, APPROVED, RETURNED, REJECTED }

    public enum View { TO_REVIEW, DECIDED, MY_REFERRALS }
}
