package com.chubb.claims.claim.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.math.BigDecimal;
import java.time.Instant;

@Embeddable
public record Referral(
        @Column(name = "referred_by") String referredBy,
        @Column(name = "referred_amount") BigDecimal amount,
        @Column(name = "referral_reason") String reason,
        @Column(name = "referred_at") Instant referredAt) {

    public enum Outcome { APPROVE, RETURN, REJECT }
}
