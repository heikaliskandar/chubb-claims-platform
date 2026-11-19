package com.chubb.claims.claim.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Immutable
@Table(name = "assessment")
public class Assessment {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id")
    private Claim claim;

    private String officerId;
    private BigDecimal assessedAmount;
    private String currency;
    private String notes;
    private Instant assessedAt;

    protected Assessment() {
    }

    Assessment(Claim claim, String officerId, BigDecimal assessedAmount, String currency,
               String notes, Instant assessedAt) {
        this.id = UUID.randomUUID();
        this.claim = claim;
        this.officerId = officerId;
        this.assessedAmount = assessedAmount;
        this.currency = currency;
        this.notes = notes;
        this.assessedAt = assessedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getOfficerId() {
        return officerId;
    }

    public BigDecimal getAssessedAmount() {
        return assessedAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getNotes() {
        return notes;
    }

    public Instant getAssessedAt() {
        return assessedAt;
    }
}
