package com.chubb.claims.claim.domain;

public record Actor(String userId, boolean handlesClaims, ApprovalLimit approvalLimit) {
}
