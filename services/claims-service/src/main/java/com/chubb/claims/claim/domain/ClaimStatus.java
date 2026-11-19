package com.chubb.claims.claim.domain;

import java.util.EnumSet;
import java.util.Set;

public enum ClaimStatus {
    SUBMITTED,
    ASSIGNED,
    UNDER_REVIEW,
    INFO_REQUESTED,
    PENDING_APPROVAL,
    APPROVED,
    REJECTED,
    SETTLED,
    WITHDRAWN;

    public enum Party { CLAIMS_TEAM, ASSIGNED_OFFICER, CLAIMANT, APPROVER, NOBODY }

    public Set<ClaimStatus> allowedNextStatuses() {
        return switch (this) {
            case SUBMITTED -> EnumSet.of(ASSIGNED, WITHDRAWN);
            case ASSIGNED -> EnumSet.of(UNDER_REVIEW);
            case UNDER_REVIEW -> EnumSet.of(INFO_REQUESTED, PENDING_APPROVAL, APPROVED, REJECTED);
            case INFO_REQUESTED -> EnumSet.of(UNDER_REVIEW);
            case PENDING_APPROVAL -> EnumSet.of(APPROVED, REJECTED, UNDER_REVIEW);
            case APPROVED -> EnumSet.of(SETTLED);
            case REJECTED, SETTLED, WITHDRAWN -> EnumSet.noneOf(ClaimStatus.class);
        };
    }

    public boolean canTransitionTo(ClaimStatus target) {
        return allowedNextStatuses().contains(target);
    }

    public boolean isTerminal() {
        return allowedNextStatuses().isEmpty();
    }

    public Party waitingOn() {
        return switch (this) {
            case SUBMITTED -> Party.CLAIMS_TEAM;
            case ASSIGNED, UNDER_REVIEW, APPROVED -> Party.ASSIGNED_OFFICER;
            case INFO_REQUESTED -> Party.CLAIMANT;
            case PENDING_APPROVAL -> Party.APPROVER;
            case REJECTED, SETTLED, WITHDRAWN -> Party.NOBODY;
        };
    }
}
