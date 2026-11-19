package com.chubb.claims.claim.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

@Entity
@Immutable
@Table(name = "claim_status_history")
public class ClaimStatusHistory {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id")
    private Claim claim;

    @Enumerated(EnumType.STRING)
    private ClaimStatus fromStatus;

    @Enumerated(EnumType.STRING)
    private ClaimStatus toStatus;

    private String changedBy;
    private String reason;
    private Instant changedAt;

    protected ClaimStatusHistory() {
    }

    ClaimStatusHistory(Claim claim, ClaimStatus fromStatus, ClaimStatus toStatus,
                       String changedBy, String reason, Instant changedAt) {
        this.id = UUID.randomUUID();
        this.claim = claim;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.changedBy = changedBy;
        this.reason = reason;
        this.changedAt = changedAt;
    }

    public UUID getId() {
        return id;
    }

    public ClaimStatus getFromStatus() {
        return fromStatus;
    }

    public ClaimStatus getToStatus() {
        return toStatus;
    }

    public String getChangedBy() {
        return changedBy;
    }

    public String getReason() {
        return reason;
    }

    public Instant getChangedAt() {
        return changedAt;
    }
}
