package com.claimsplatform.claims.claim.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.math.BigDecimal;
import java.time.Instant;

@Embeddable
public record Decision(
        @Enumerated(EnumType.STRING) @Column(name = "decision_outcome") Outcome outcome,
        @Column(name = "approved_amount") BigDecimal approvedAmount,
        @Column(name = "decision_reason") String reason,
        @Column(name = "decided_by") String decidedBy,
        @Column(name = "decided_at") Instant decidedAt) {

    public enum Outcome { APPROVED, REJECTED }
}
