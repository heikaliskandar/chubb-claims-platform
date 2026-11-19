package com.chubb.claims.claim.application;

import com.chubb.claims.claim.api.dto.ClaimDetailResponse;
import com.chubb.claims.claim.api.dto.ClaimDetailResponse.AssessmentResponse;
import com.chubb.claims.claim.api.dto.ClaimDetailResponse.DecisionResponse;
import com.chubb.claims.claim.api.dto.ClaimDetailResponse.InformationRequestResponse;
import com.chubb.claims.claim.api.dto.ClaimDetailResponse.StatusChangeResponse;
import com.chubb.claims.claim.api.dto.ClaimSummaryResponse;
import com.chubb.claims.claim.domain.Assessment;
import com.chubb.claims.claim.domain.Claim;
import com.chubb.claims.claim.domain.ClaimStatusHistory;
import com.chubb.claims.claim.domain.Decision;
import com.chubb.claims.claim.domain.InformationRequest;

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
                claim.getCurrency(),
                claim.getClaimedAmount(),
                claim.getAssignedOfficerId(),
                claim.getIncidentDate(),
                claim.getCreatedAt(),
                claim.getUpdatedAt());
    }

    static ClaimDetailResponse toDetail(Claim claim, boolean includeInternalFields) {
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
                claim.getAssignedOfficerId(),
                includeInternalFields ? claim.getReserveAmount() : null,
                toDecision(claim.getDecision()),
                claim.getStatusHistory().stream().map(ClaimMapper::toStatusChange).toList(),
                claim.getInformationRequests().stream().map(ClaimMapper::toInformationRequest).toList(),
                includeInternalFields ? claim.getAssessments().stream().map(ClaimMapper::toAssessment).toList() : null,
                claim.getCreatedAt(),
                claim.getUpdatedAt());
    }

    private static DecisionResponse toDecision(Decision decision) {
        if (decision == null) {
            return null;
        }
        return new DecisionResponse(decision.outcome(), decision.approvedAmount(), decision.reason(),
                decision.decidedBy(), decision.decidedAt());
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
