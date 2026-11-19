package com.chubb.claims.claim.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "claim")
public class Claim {

    private static final int MONEY_SCALE = 2;
    private static final BigDecimal ZERO_MONEY = BigDecimal.ZERO.setScale(MONEY_SCALE);

    @Id
    private UUID id;

    private String claimNumber;
    private String policyNumber;
    private String claimantId;

    @Enumerated(EnumType.STRING)
    private Market market;

    @Enumerated(EnumType.STRING)
    private ClaimType claimType;

    private LocalDate incidentDate;
    private String incidentDescription;
    private String currency;
    private BigDecimal claimedAmount;
    private BigDecimal reserveAmount;

    @Enumerated(EnumType.STRING)
    private ClaimStatus status;

    private String assignedOfficerId;

    @Embedded
    private Decision decision;

    private String idempotencyKey;

    @Version
    private Long version;

    private Instant createdAt;
    private Instant updatedAt;

    @OneToMany(mappedBy = "claim", cascade = CascadeType.ALL)
    @OrderBy("changedAt")
    private List<ClaimStatusHistory> statusHistory = new ArrayList<>();

    @OneToMany(mappedBy = "claim", cascade = CascadeType.ALL)
    @OrderBy("requestedAt")
    private List<InformationRequest> informationRequests = new ArrayList<>();

    @OneToMany(mappedBy = "claim", cascade = CascadeType.ALL)
    @OrderBy("assessedAt")
    private List<Assessment> assessments = new ArrayList<>();

    protected Claim() {
    }

    public static Claim submit(String claimNumber, String claimantId, ClaimDetails details,
                               String idempotencyKey, Instant now) {
        Claim claim = new Claim();
        claim.id = UUID.randomUUID();
        claim.claimNumber = Objects.requireNonNull(claimNumber);
        claim.claimantId = Objects.requireNonNull(claimantId);
        claim.policyNumber = Objects.requireNonNull(details.policyNumber());
        claim.market = Objects.requireNonNull(details.market());
        claim.claimType = Objects.requireNonNull(details.claimType());
        claim.incidentDate = Objects.requireNonNull(details.incidentDate());
        claim.incidentDescription = Objects.requireNonNull(details.incidentDescription());
        claim.currency = details.market().currency();
        claim.claimedAmount = requirePositive(toMoney(details.claimedAmount(), "claimedAmount"), "claimedAmount");
        claim.reserveAmount = ZERO_MONEY;
        claim.idempotencyKey = idempotencyKey;
        claim.createdAt = now;
        claim.recordStatusChange(ClaimStatus.SUBMITTED, claimantId, null, now);
        return claim;
    }

    public void assignTo(String officerId, Instant now) {
        transitionTo(ClaimStatus.ASSIGNED, officerId, null, now);
        this.assignedOfficerId = officerId;
    }

    public void startReview(String officerId, Instant now) {
        requireAssignedOfficer(officerId);
        transitionTo(ClaimStatus.UNDER_REVIEW, officerId, null, now);
    }

    public InformationRequest requestInformation(String officerId, String question, Instant now) {
        requireAssignedOfficer(officerId);
        requireText(question, "question");
        transitionTo(ClaimStatus.INFO_REQUESTED, officerId, null, now);
        InformationRequest request = new InformationRequest(this, officerId, question, now);
        informationRequests.add(request);
        return request;
    }

    public void provideInformation(UUID requestId, String claimantId, String response, Instant now) {
        requireClaimant(claimantId);
        requireText(response, "response");
        InformationRequest request = findInformationRequest(requestId);
        if (!request.isOpen()) {
            throw new ClaimRuleViolationException("Information request " + requestId + " has already been answered");
        }
        transitionTo(ClaimStatus.UNDER_REVIEW, claimantId, null, now);
        request.answer(response, now);
    }

    public void assess(String officerId, BigDecimal assessedAmount, String notes, Instant now) {
        requireAssignedOfficer(officerId);
        if (status != ClaimStatus.UNDER_REVIEW) {
            throw new InvalidClaimStateException("Cannot assess a claim in status " + status);
        }
        BigDecimal amount = toMoney(assessedAmount, "assessedAmount");
        assessments.add(new Assessment(this, officerId, amount, currency, notes, now));
        this.reserveAmount = amount;
        this.updatedAt = now;
    }

    public void approve(String officerId, BigDecimal approvedAmount, String reason, Instant now) {
        requireAssignedOfficer(officerId);
        requireCanTransitionTo(ClaimStatus.APPROVED);
        if (assessments.isEmpty()) {
            throw new ClaimRuleViolationException("A claim must be assessed before it can be approved");
        }
        BigDecimal amount = requirePositive(toMoney(approvedAmount, "approvedAmount"), "approvedAmount");
        if (amount.compareTo(claimedAmount) > 0) {
            throw new ClaimRuleViolationException("Approved amount cannot exceed the claimed amount of " + claimedAmount);
        }
        transitionTo(ClaimStatus.APPROVED, officerId, reason, now);
        this.decision = new Decision(Decision.Outcome.APPROVED, amount, reason, officerId, now);
        this.reserveAmount = amount;
    }

    public void reject(String officerId, String reason, Instant now) {
        requireAssignedOfficer(officerId);
        requireText(reason, "reason");
        transitionTo(ClaimStatus.REJECTED, officerId, reason, now);
        this.decision = new Decision(Decision.Outcome.REJECTED, null, reason, officerId, now);
        this.reserveAmount = ZERO_MONEY;
    }

    public void settle(String officerId, Instant now) {
        requireAssignedOfficer(officerId);
        transitionTo(ClaimStatus.SETTLED, officerId, null, now);
        this.reserveAmount = ZERO_MONEY;
    }

    private void transitionTo(ClaimStatus target, String actor, String reason, Instant now) {
        requireCanTransitionTo(target);
        recordStatusChange(target, actor, reason, now);
    }

    private void recordStatusChange(ClaimStatus target, String actor, String reason, Instant now) {
        statusHistory.add(new ClaimStatusHistory(this, status, target, actor, reason, now));
        this.status = target;
        this.updatedAt = now;
    }

    private void requireCanTransitionTo(ClaimStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidClaimStateException("Cannot move claim from " + status + " to " + target);
        }
    }

    private void requireAssignedOfficer(String officerId) {
        if (!Objects.equals(assignedOfficerId, officerId)) {
            throw new ClaimAccessDeniedException("Only the assigned officer can act on this claim");
        }
    }

    private void requireClaimant(String userId) {
        if (!claimantId.equals(userId)) {
            throw new ClaimAccessDeniedException("Only the claimant can respond on this claim");
        }
    }

    private InformationRequest findInformationRequest(UUID requestId) {
        return informationRequests.stream()
                .filter(request -> request.getId().equals(requestId))
                .findFirst()
                .orElseThrow(() -> new InformationRequestNotFoundException(requestId));
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ClaimRuleViolationException(field + " must not be blank");
        }
    }

    private static BigDecimal toMoney(BigDecimal amount, String field) {
        if (amount == null || amount.signum() < 0) {
            throw new ClaimRuleViolationException(field + " must not be negative");
        }
        if (amount.stripTrailingZeros().scale() > MONEY_SCALE) {
            throw new ClaimRuleViolationException(field + " must have at most " + MONEY_SCALE + " decimal places");
        }
        return amount.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
    }

    private static BigDecimal requirePositive(BigDecimal amount, String field) {
        if (amount.signum() == 0) {
            throw new ClaimRuleViolationException(field + " must be greater than zero");
        }
        return amount;
    }

    public UUID getId() {
        return id;
    }

    public String getClaimNumber() {
        return claimNumber;
    }

    public String getPolicyNumber() {
        return policyNumber;
    }

    public String getClaimantId() {
        return claimantId;
    }

    public Market getMarket() {
        return market;
    }

    public ClaimType getClaimType() {
        return claimType;
    }

    public LocalDate getIncidentDate() {
        return incidentDate;
    }

    public String getIncidentDescription() {
        return incidentDescription;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getClaimedAmount() {
        return claimedAmount;
    }

    public BigDecimal getReserveAmount() {
        return reserveAmount;
    }

    public ClaimStatus getStatus() {
        return status;
    }

    public String getAssignedOfficerId() {
        return assignedOfficerId;
    }

    public Decision getDecision() {
        return decision;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<ClaimStatusHistory> getStatusHistory() {
        return Collections.unmodifiableList(statusHistory);
    }

    public List<InformationRequest> getInformationRequests() {
        return Collections.unmodifiableList(informationRequests);
    }

    public List<Assessment> getAssessments() {
        return Collections.unmodifiableList(assessments);
    }
}
