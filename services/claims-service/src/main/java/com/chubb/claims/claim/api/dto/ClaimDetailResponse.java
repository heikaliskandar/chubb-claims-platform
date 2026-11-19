package com.chubb.claims.claim.api.dto;

import com.chubb.claims.claim.domain.ClaimAction;
import com.chubb.claims.claim.domain.ClaimStatus;
import com.chubb.claims.claim.domain.ClaimType;
import com.chubb.claims.claim.domain.Decision;
import com.chubb.claims.claim.domain.InformationRequest;
import com.chubb.claims.claim.domain.Market;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record ClaimDetailResponse(
        UUID id,
        String claimNumber,
        String policyNumber,
        String claimantId,
        Market market,
        ClaimType claimType,
        LocalDate incidentDate,
        String incidentDescription,
        String currency,
        BigDecimal claimedAmount,
        ClaimStatus status,
        ClaimStatus.Party waitingOn,
        String nextStep,
        Set<ClaimAction> allowedActions,
        String assignedOfficerId,
        @Schema(description = "Internal estimate of liability. Null for claimants.", nullable = true)
        BigDecimal reserveAmount,
        DecisionResponse decision,
        @Schema(description = "Latest referral for approval above the officer's authority. Null for claimants.",
                nullable = true)
        ReferralResponse referral,
        List<StatusChangeResponse> statusHistory,
        List<InformationRequestResponse> informationRequests,
        @Schema(description = "Officer assessments. Null for claimants.", nullable = true)
        List<AssessmentResponse> assessments,
        Instant createdAt,
        Instant updatedAt,
        @Schema(description = "Whether the current user can attach supporting documents to this claim.")
        boolean canUploadDocuments) {

    public record DecisionResponse(
            Decision.Outcome outcome,
            BigDecimal approvedAmount,
            String reason,
            String decidedBy,
            Instant decidedAt) {
    }

    public record ReferralResponse(
            String referredBy,
            BigDecimal amount,
            String reason,
            Instant referredAt) {
    }

    public record StatusChangeResponse(
            ClaimStatus fromStatus,
            ClaimStatus toStatus,
            String changedBy,
            String reason,
            Instant changedAt) {
    }

    public record InformationRequestResponse(
            UUID id,
            String requestedBy,
            String question,
            String response,
            InformationRequest.Status status,
            Instant requestedAt,
            Instant answeredAt) {
    }

    public record AssessmentResponse(
            String officerId,
            BigDecimal assessedAmount,
            String currency,
            String notes,
            Instant assessedAt) {
    }
}
