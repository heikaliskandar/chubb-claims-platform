package com.claimsplatform.claims.claim.application;

import com.claimsplatform.claims.claim.api.dto.ApprovalResponse;
import com.claimsplatform.claims.claim.domain.Claim;
import com.claimsplatform.claims.claim.domain.ClaimStatus;
import com.claimsplatform.claims.claim.domain.ClaimStatusHistory;
import com.claimsplatform.claims.claim.domain.Referral;

import java.util.Optional;

final class ApprovalMapper {

    private ApprovalMapper() {
    }

    static ApprovalResponse toApproval(Claim claim, boolean canReview) {
        Referral referral = claim.getReferral();
        Optional<ClaimStatusHistory> review = latestReview(claim);
        return new ApprovalResponse(
                claim.getId(),
                claim.getClaimNumber(),
                claim.getMarket(),
                claim.getClaimType(),
                claim.getCurrency(),
                claim.getClaimedAmount(),
                referral.amount(),
                referral.reason(),
                referral.referredBy(),
                referral.referredAt(),
                statusOf(claim, review),
                review.map(ClaimStatusHistory::getChangedBy).orElse(null),
                review.map(ClaimStatusHistory::getChangedAt).orElse(null),
                canReview);
    }

    static ApprovalResponse.Status statusOf(Claim claim, Optional<ClaimStatusHistory> review) {
        if (claim.getStatus() == ClaimStatus.PENDING_APPROVAL || review.isEmpty()) {
            return ApprovalResponse.Status.PENDING;
        }
        return switch (review.get().getToStatus()) {
            case APPROVED -> ApprovalResponse.Status.APPROVED;
            case REJECTED -> ApprovalResponse.Status.REJECTED;
            default -> ApprovalResponse.Status.RETURNED;
        };
    }

    static Optional<ClaimStatusHistory> latestReview(Claim claim) {
        if (claim.getStatus() == ClaimStatus.PENDING_APPROVAL) {
            return Optional.empty();
        }
        return claim.getStatusHistory().stream()
                .filter(entry -> entry.getFromStatus() == ClaimStatus.PENDING_APPROVAL)
                .reduce((first, second) -> second);
    }
}
