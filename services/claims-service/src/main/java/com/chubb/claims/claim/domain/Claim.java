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
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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

    @Embedded
    private Referral referral;

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
        requireStatus(ClaimStatus.UNDER_REVIEW, "assess");
        BigDecimal amount = toMoney(assessedAmount, "assessedAmount");
        assessments.add(new Assessment(this, officerId, amount, currency, notes, now));
        this.reserveAmount = amount;
        this.updatedAt = now;
    }

    public void approve(String officerId, BigDecimal approvedAmount, String reason, ApprovalLimit authority,
                        Instant now) {
        requireAssignedOfficer(officerId);
        requireStatus(ClaimStatus.UNDER_REVIEW, "approve");
        if (assessments.isEmpty()) {
            throw new ClaimRuleViolationException("A claim must be assessed before it can be approved");
        }
        BigDecimal amount = requirePositive(toMoney(approvedAmount, "approvedAmount"), "approvedAmount");
        if (amount.compareTo(claimedAmount) > 0) {
            throw new ClaimRuleViolationException("Approved amount cannot exceed the claimed amount of " + claimedAmount);
        }
        if (authority.covers(amount)) {
            transitionTo(ClaimStatus.APPROVED, officerId, reason, now);
            recordApproval(amount, reason, officerId, now);
        } else {
            transitionTo(ClaimStatus.PENDING_APPROVAL, officerId, reason, now);
            this.referral = new Referral(officerId, amount, reason, now);
        }
    }

    public void reject(String officerId, String reason, Instant now) {
        requireAssignedOfficer(officerId);
        requireStatus(ClaimStatus.UNDER_REVIEW, "reject");
        requireText(reason, "reason");
        transitionTo(ClaimStatus.REJECTED, officerId, reason, now);
        recordRejection(reason, officerId, now);
    }

    public void reviewReferral(String approverId, ApprovalLimit authority, Referral.Outcome outcome,
                               String comment, Instant now) {
        if (status != ClaimStatus.PENDING_APPROVAL) {
            throw new InvalidClaimStateException("Claim is not awaiting approval");
        }
        if (referral.referredBy().equals(approverId)) {
            throw new ClaimAccessDeniedException("Four-eyes rule: you cannot approve your own recommendation");
        }
        if (!authority.covers(referral.amount())) {
            throw new ClaimAccessDeniedException("The recommended amount exceeds your approval authority");
        }
        switch (outcome) {
            case APPROVE -> {
                transitionTo(ClaimStatus.APPROVED, approverId, comment, now);
                recordApproval(referral.amount(), referral.reason(), approverId, now);
            }
            case RETURN -> {
                requireText(comment, "comment");
                transitionTo(ClaimStatus.UNDER_REVIEW, approverId, comment, now);
            }
            case REJECT -> {
                requireText(comment, "comment");
                transitionTo(ClaimStatus.REJECTED, approverId, comment, now);
                recordRejection(comment, approverId, now);
            }
        }
    }

    public Set<ClaimAction> availableActions(Actor actor) {
        Set<ClaimAction> actions = EnumSet.noneOf(ClaimAction.class);
        if (status == ClaimStatus.INFO_REQUESTED && claimantId.equals(actor.userId())) {
            actions.add(ClaimAction.RESPOND_TO_INFORMATION_REQUEST);
        }
        if (status == ClaimStatus.PENDING_APPROVAL && isEligibleApprover(actor)) {
            actions.addAll(EnumSet.of(ClaimAction.APPROVE_REFERRAL, ClaimAction.RETURN_REFERRAL,
                    ClaimAction.REJECT_REFERRAL));
        }
        if (!actor.handlesClaims()) {
            return actions;
        }
        boolean assignedToActor = actor.userId().equals(assignedOfficerId);
        switch (status) {
            case SUBMITTED -> actions.add(ClaimAction.ASSIGN_TO_ME);
            case ASSIGNED -> addIf(actions, assignedToActor, ClaimAction.START_REVIEW);
            case UNDER_REVIEW -> {
                addIf(actions, assignedToActor, ClaimAction.REQUEST_INFORMATION);
                addIf(actions, assignedToActor, ClaimAction.ASSESS);
                addIf(actions, assignedToActor, ClaimAction.REJECT);
                addIf(actions, assignedToActor && !assessments.isEmpty(), ClaimAction.APPROVE);
            }
            case APPROVED -> addIf(actions, assignedToActor, ClaimAction.SETTLE);
            default -> {
            }
        }
        return actions;
    }

    private boolean isEligibleApprover(Actor actor) {
        return !referral.referredBy().equals(actor.userId()) && actor.approvalLimit().covers(referral.amount());
    }

    private static void addIf(Set<ClaimAction> actions, boolean condition, ClaimAction action) {
        if (condition) {
            actions.add(action);
        }
    }

    private void recordApproval(BigDecimal amount, String reason, String decidedBy, Instant now) {
        this.decision = new Decision(Decision.Outcome.APPROVED, amount, reason, decidedBy, now);
        this.reserveAmount = amount;
    }

    private void recordRejection(String reason, String decidedBy, Instant now) {
        this.decision = new Decision(Decision.Outcome.REJECTED, null, reason, decidedBy, now);
        this.reserveAmount = ZERO_MONEY;
    }

    private void requireStatus(ClaimStatus required, String operation) {
        if (status != required) {
            throw new InvalidClaimStateException("Cannot " + operation + " a claim in status " + status);
        }
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
        if (amount == null) {
            throw new ClaimRuleViolationException(field + " is required");
        }
        if (amount.signum() < 0) {
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

    public Referral getReferral() {
        return referral;
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
