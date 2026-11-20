package com.claimsplatform.claims.claim.domain;

import java.util.Set;

public record Actor(String userId, boolean handlesClaims, ApprovalLimit approvalLimit, Set<String> reports) {

    public Actor {
        reports = Set.copyOf(reports);
    }

    public boolean manages(String userId) {
        return reports.contains(userId);
    }
}
