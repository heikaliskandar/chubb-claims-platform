package com.chubb.claims.claim.application;

import com.chubb.claims.claim.api.dto.ClaimDetailResponse;
import com.chubb.claims.claim.api.dto.ClaimDetailResponse.AssessmentResponse;
import com.chubb.claims.claim.api.dto.ClaimDetailResponse.DecisionResponse;
import com.chubb.claims.claim.api.dto.ClaimDetailResponse.InformationRequestResponse;
import com.chubb.claims.claim.api.dto.ClaimDetailResponse.ReferralResponse;
import com.chubb.claims.claim.api.dto.ClaimDetailResponse.StatusChangeResponse;
import com.chubb.claims.claim.api.dto.ClaimSummaryResponse;
import com.chubb.claims.claim.domain.Assessment;
import com.chubb.claims.claim.domain.Claim;
import com.chubb.claims.claim.domain.ClaimAction;
import com.chubb.claims.claim.domain.ClaimStatus;
import com.chubb.claims.claim.domain.ClaimStatusHistory;
import com.chubb.claims.claim.domain.Decision;
import com.chubb.claims.claim.domain.InformationRequest;
import com.chubb.claims.claim.domain.Referral;

import java.util.Set;

final class ClaimMapper {

    private ClaimMapper() {
    }

    static ClaimSummaryResponse toSummary(Claim claim) {
        return new ClaimSummaryResponse(
                claim.getId(),
                claim.getClaimNumber(),
                claim.getMarket(),
                claim.getClaimType(),
                claim.getStatus(),
                claim.getStatus().waitingOn(),
                claim.getCurrency(),
                claim.getClaimedAmount(),
                claim.getAssignedOfficerId(),
                claim.getIncidentDate(),
                claim.getCreatedAt(),
                claim.getUpdatedAt());
    }

    static ClaimDetailResponse toDetail(Claim claim, boolean includeInternalFields, Set<ClaimAction> allowedActions) {
        return new ClaimDetailResponse(
                claim.getId(),
                claim.getClaimNumber(),
                claim.getPolicyNumber(),
                claim.getClaimantId(),
                claim.getMarket(),
                claim.getClaimType(),
                claim.getIncidentDate(),
                claim.getIncidentDescription(),
                claim.getCurrency(),
                claim.getClaimedAmount(),
                claim.getStatus(),
                claim.getStatus().waitingOn(),
                nextStep(claim.getStatus()),
                allowedActions,
                claim.getAssignedOfficerId(),
                includeInternalFields ? claim.getReserveAmount() : null,
                toDecision(claim.getDecision()),
                includeInternalFields ? toReferral(claim.getReferral()) : null,
                claim.getStatusHistory().stream().map(ClaimMapper::toStatusChange).toList(),
                claim.getInformationRequests().stream().map(ClaimMapper::toInformationRequest).toList(),
                includeInternalFields ? claim.getAssessments().stream().map(ClaimMapper::toAssessment).toList() : null,
                claim.getCreatedAt(),
                claim.getUpdatedAt());
    }

    private static String nextStep(ClaimStatus status) {
        return switch (status) {
            case SUBMITTED -> "Waiting for a claims officer to pick up the claim.";
            case ASSIGNED -> "The assigned officer will start reviewing the claim.";
            case UNDER_REVIEW -> "The officer is reviewing and assessing the claim.";
            case INFO_REQUESTED -> "Waiting for the claimant to answer the information request.";
            case PENDING_APPROVAL -> "The recommended amount is above the officer's authority and needs approval.";
            case APPROVED -> "Approved. Waiting for settlement to be recorded.";
            case REJECTED -> "Closed: the claim was rejected.";
            case SETTLED -> "Closed: the claim has been settled.";
        };
    }

    private static DecisionResponse toDecision(Decision decision) {
        if (decision == null) {
            return null;
        }
        return new DecisionResponse(decision.outcome(), decision.approvedAmount(), decision.reason(),
                decision.decidedBy(), decision.decidedAt());
    }

    private static ReferralResponse toReferral(Referral referral) {
        if (referral == null) {
            return null;
        }
        return new ReferralResponse(referral.referredBy(), referral.amount(), referral.reason(), referral.referredAt());
    }

    private static StatusChangeResponse toStatusChange(ClaimStatusHistory entry) {
        return new StatusChangeResponse(entry.getFromStatus(), entry.getToStatus(), entry.getChangedBy(),
                entry.getReason(), entry.getChangedAt());
    }

    private static InformationRequestResponse toInformationRequest(InformationRequest request) {
        return new InformationRequestResponse(request.getId(), request.getRequestedBy(), request.getQuestion(),
                request.getResponse(), request.getStatus(), request.getRequestedAt(), request.getAnsweredAt());
    }

    private static AssessmentResponse toAssessment(Assessment assessment) {
        return new AssessmentResponse(assessment.getOfficerId(), assessment.getAssessedAmount(),
                assessment.getCurrency(), assessment.getNotes(), assessment.getAssessedAt());
    }
}
