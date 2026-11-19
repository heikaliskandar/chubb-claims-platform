package com.chubb.claims.claim.application;

import com.chubb.claims.claim.api.dto.ApprovalResponse;
import com.chubb.claims.claim.api.dto.ClaimDetailResponse;
import com.chubb.claims.claim.api.dto.ClaimSummaryResponse;
import com.chubb.claims.claim.domain.Actor;
import com.chubb.claims.claim.domain.ApprovalLimit;
import com.chubb.claims.claim.domain.Claim;
import com.chubb.claims.claim.domain.ClaimAction;
import com.chubb.claims.claim.domain.ClaimDetails;
import com.chubb.claims.claim.domain.ClaimNumber;
import com.chubb.claims.claim.domain.ClaimRuleViolationException;
import com.chubb.claims.claim.domain.ClaimStatus;
import com.chubb.claims.claim.domain.Decision;
import com.chubb.claims.claim.domain.Referral;
import com.chubb.claims.claim.infrastructure.ClaimRepository;
import com.chubb.claims.claim.infrastructure.ClaimSpecifications;
import com.chubb.claims.common.security.CurrentUser;
import com.chubb.claims.common.web.PageResponse;
import com.chubb.claims.employee.application.EmployeeService;
import com.chubb.claims.events.ClaimEventRecorder;
import com.chubb.claims.events.ClaimEventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;

@Service
@Transactional
public class ClaimService {

    private static final Logger log = LoggerFactory.getLogger(ClaimService.class);

    private final ClaimRepository claims;
    private final ClaimEventRecorder events;
    private final AuthorityLimits authorityLimits;
    private final EmployeeService employees;
    private final Clock clock;

    public ClaimService(ClaimRepository claims, ClaimEventRecorder events, AuthorityLimits authorityLimits,
                        EmployeeService employees, Clock clock) {
        this.claims = claims;
        this.events = events;
        this.authorityLimits = authorityLimits;
        this.employees = employees;
        this.clock = clock;
    }

    public ClaimSubmission submit(CurrentUser claimant, ClaimDetails details, String idempotencyKey) {
        if (idempotencyKey != null) {
            Optional<Claim> existing = claims.findByClaimantIdAndIdempotencyKey(claimant.username(), idempotencyKey);
            if (existing.isPresent()) {
                return new ClaimSubmission(toDetail(existing.get(), claimant, Set.of()), false);
            }
        }
        Instant now = clock.instant();
        String claimNumber = ClaimNumber.format(details.market(), now.atZone(ZoneOffset.UTC).getYear(),
                claims.nextClaimNumberSequence());
        Claim claim = claims.save(Claim.submit(claimNumber, claimant.username(), details, idempotencyKey, now));
        events.record(ClaimEventType.CLAIM_SUBMITTED, claim, claimant.username());
        logStatus(claim);
        return new ClaimSubmission(toDetail(claim, claimant, Set.of()), true);
    }

    @Transactional(readOnly = true)
    public PageResponse<ClaimSummaryResponse> findClaims(CurrentUser viewer, ClaimStatus status,
                                                         String assignedOfficerId, Boolean closed,
                                                         int page, int size) {
        Set<String> officers = new HashSet<>(reportsOf(viewer));
        officers.add(viewer.username());
        Specification<Claim> query = ClaimSpecifications.matching(status, assignedOfficerId, closed)
                .and(ClaimSpecifications.unassignedOrAssignedTo(officers));
        Sort sort = Boolean.TRUE.equals(closed) ? Sort.by("updatedAt").descending() : Sort.by("createdAt");
        return PageResponse.of(claims.findAll(query, PageRequest.of(page, size, sort)).map(ClaimMapper::toSummary));
    }

    @Transactional(readOnly = true)
    public PageResponse<ApprovalResponse> findApprovals(CurrentUser viewer, ApprovalResponse.View view,
                                                        int page, int size) {
        Set<String> reports = reportsOf(viewer);
        Specification<Claim> query = switch (view) {
            case TO_REVIEW -> ClaimSpecifications.referredBy(reports).and(ClaimSpecifications.awaitingApproval(true));
            case DECIDED -> ClaimSpecifications.referredBy(reports).and(ClaimSpecifications.awaitingApproval(false));
            case MY_REFERRALS -> ClaimSpecifications.referredBy(Set.of(viewer.username()));
        };
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("referral.referredAt").descending());
        if (view != ApprovalResponse.View.MY_REFERRALS && reports.isEmpty()) {
            return PageResponse.of(Page.empty(pageRequest));
        }
        return PageResponse.of(claims.findAll(query, pageRequest).map(claim -> ApprovalMapper.toApproval(claim,
                claim.availableActions(actorFor(viewer, claim, reports)).contains(ClaimAction.APPROVE_REFERRAL))));
    }

    @Transactional(readOnly = true)
    public PageResponse<ClaimSummaryResponse> findClaimsOfClaimant(String claimantId, Boolean closed,
                                                                   int page, int size) {
        Specification<Claim> query = ClaimSpecifications.ofClaimant(claimantId)
                .and(ClaimSpecifications.matching(null, null, closed));
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("updatedAt").descending());
        return PageResponse.of(claims.findAll(query, pageRequest).map(ClaimMapper::toSummary));
    }

    @Transactional(readOnly = true)
    public ClaimDetailResponse getClaim(UUID claimId, CurrentUser user) {
        Set<String> reports = reportsOf(user);
        return toDetail(loadFor(claimId, user, reports), user, reports);
    }

    public ClaimDetailResponse assign(UUID claimId, CurrentUser officer) {
        return act(claimId, officer, (claim, actor) -> {
            claim.assignTo(officer.username(), clock.instant());
            return ClaimEventType.CLAIM_ASSIGNED;
        });
    }

    public ClaimDetailResponse startReview(UUID claimId, CurrentUser officer) {
        return act(claimId, officer, (claim, actor) -> {
            claim.startReview(officer.username(), clock.instant());
            return ClaimEventType.CLAIM_REVIEW_STARTED;
        });
    }

    public ClaimDetailResponse requestInformation(UUID claimId, CurrentUser officer, String question) {
        return act(claimId, officer, (claim, actor) -> {
            claim.requestInformation(officer.username(), question, clock.instant());
            return ClaimEventType.INFORMATION_REQUESTED;
        });
    }

    public ClaimDetailResponse provideInformation(UUID claimId, UUID requestId, CurrentUser claimant,
                                                  String response) {
        return act(claimId, claimant, (claim, actor) -> {
            claim.provideInformation(requestId, claimant.username(), response, clock.instant());
            return ClaimEventType.INFORMATION_PROVIDED;
        });
    }

    public ClaimDetailResponse assess(UUID claimId, CurrentUser officer, BigDecimal assessedAmount, String notes) {
        return act(claimId, officer, (claim, actor) -> {
            claim.assess(officer.username(), assessedAmount, notes, clock.instant());
            return ClaimEventType.CLAIM_ASSESSED;
        });
    }

    public ClaimDetailResponse decide(UUID claimId, CurrentUser officer, Decision.Outcome outcome,
                                      BigDecimal approvedAmount, String reason) {
        return act(claimId, officer, (claim, actor) -> switch (outcome) {
            case APPROVED -> {
                claim.approve(officer.username(), approvedAmount, reason, limitOf(officer, claim), clock.instant());
                yield claim.getStatus() == ClaimStatus.PENDING_APPROVAL
                        ? ClaimEventType.CLAIM_REFERRED_FOR_APPROVAL
                        : ClaimEventType.CLAIM_APPROVED;
            }
            case REJECTED -> {
                if (approvedAmount != null) {
                    throw new ClaimRuleViolationException("approvedAmount must not be set when rejecting a claim");
                }
                claim.reject(officer.username(), reason, clock.instant());
                yield ClaimEventType.CLAIM_REJECTED;
            }
        });
    }

    public ClaimDetailResponse reviewReferral(UUID claimId, CurrentUser approver, Referral.Outcome outcome,
                                              String comment) {
        return act(claimId, approver, (claim, actor) -> {
            claim.reviewReferral(actor, outcome, comment, clock.instant());
            return switch (outcome) {
                case APPROVE -> ClaimEventType.CLAIM_APPROVED;
                case RETURN -> ClaimEventType.REFERRAL_RETURNED;
                case REJECT -> ClaimEventType.CLAIM_REJECTED;
            };
        });
    }

    public ClaimDetailResponse settle(UUID claimId, CurrentUser officer) {
        return act(claimId, officer, (claim, actor) -> {
            claim.settle(officer.username(), clock.instant());
            return ClaimEventType.CLAIM_SETTLED;
        });
    }

    private ClaimDetailResponse act(UUID claimId, CurrentUser user,
                                    BiFunction<Claim, Actor, ClaimEventType> command) {
        Set<String> reports = reportsOf(user);
        Claim claim = loadFor(claimId, user, reports);
        ClaimEventType eventType = command.apply(claim, actorFor(user, claim, reports));
        events.record(eventType, claim, user.username());
        logStatus(claim);
        return toDetail(claim, user, reports);
    }

    private Claim loadFor(UUID claimId, CurrentUser user, Set<String> reports) {
        Claim claim = claims.findById(claimId).orElseThrow(() -> new ClaimNotFoundException(claimId));
        if (!isVisibleTo(claim, user, reports)) {
            throw new ClaimNotFoundException(claimId);
        }
        return claim;
    }

    private static boolean isVisibleTo(Claim claim, CurrentUser user, Set<String> reports) {
        if (user.isClaimant()) {
            return claim.getClaimantId().equals(user.username());
        }
        String officer = claim.getAssignedOfficerId();
        return officer == null || officer.equals(user.username()) || reports.contains(officer);
    }

    private Set<String> reportsOf(CurrentUser user) {
        return user.isClaimant() ? Set.of() : employees.reportsOf(user.username());
    }

    private ClaimDetailResponse toDetail(Claim claim, CurrentUser user, Set<String> reports) {
        Actor actor = actorFor(user, claim, reports);
        return ClaimMapper.toDetail(claim, !user.isClaimant(), claim.availableActions(actor));
    }

    private Actor actorFor(CurrentUser user, Claim claim, Set<String> reports) {
        return new Actor(user.username(), user.isOfficer(), limitOf(user, claim), reports);
    }

    private ApprovalLimit limitOf(CurrentUser user, Claim claim) {
        return authorityLimits.limitFor(user.position(), claim.getCurrency());
    }

    private static void logStatus(Claim claim) {
        log.info("Claim {} is {}", claim.getClaimNumber(), claim.getStatus());
    }
}
