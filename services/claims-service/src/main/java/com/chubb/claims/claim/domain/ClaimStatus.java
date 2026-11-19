package com.chubb.claims.claim.domain;

import java.util.EnumSet;
import java.util.Set;

public enum ClaimStatus {
    SUBMITTED,
    ASSIGNED,
    UNDER_REVIEW,
    INFO_REQUESTED,
    APPROVED,
    REJECTED,
    SETTLED;

    public Set<ClaimStatus> allowedNextStatuses() {
        return switch (this) {
            case SUBMITTED -> EnumSet.of(ASSIGNED);
            case ASSIGNED -> EnumSet.of(UNDER_REVIEW);
            case UNDER_REVIEW -> EnumSet.of(INFO_REQUESTED, APPROVED, REJECTED);
            case INFO_REQUESTED -> EnumSet.of(UNDER_REVIEW);
            case APPROVED -> EnumSet.of(SETTLED);
            case REJECTED, SETTLED -> EnumSet.noneOf(ClaimStatus.class);
        };
    }

    public boolean canTransitionTo(ClaimStatus target) {
        return allowedNextStatuses().contains(target);
    }

    public boolean isTerminal() {
        return allowedNextStatuses().isEmpty();
    }
}
