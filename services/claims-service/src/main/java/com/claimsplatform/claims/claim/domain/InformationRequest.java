package com.claimsplatform.claims.claim.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "information_request")
public class InformationRequest {

    public enum Status { OPEN, ANSWERED }

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id")
    private Claim claim;

    private String requestedBy;
    private String question;
    private String response;

    @Enumerated(EnumType.STRING)
    private Status status;

    private Instant requestedAt;
    private Instant answeredAt;

    protected InformationRequest() {
    }

    InformationRequest(Claim claim, String requestedBy, String question, Instant requestedAt) {
        this.id = UUID.randomUUID();
        this.claim = claim;
        this.requestedBy = requestedBy;
        this.question = question;
        this.status = Status.OPEN;
        this.requestedAt = requestedAt;
    }

    void answer(String response, Instant answeredAt) {
        this.response = response;
        this.status = Status.ANSWERED;
        this.answeredAt = answeredAt;
    }

    public boolean isOpen() {
        return status == Status.OPEN;
    }

    public UUID getId() {
        return id;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public String getQuestion() {
        return question;
    }

    public String getResponse() {
        return response;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Instant getAnsweredAt() {
        return answeredAt;
    }
}
