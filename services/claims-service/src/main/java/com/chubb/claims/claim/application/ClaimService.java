package com.chubb.claims.claim.application;

import com.chubb.claims.claim.api.dto.ClaimDetailResponse;
import com.chubb.claims.claim.api.dto.ClaimSummaryResponse;
import com.chubb.claims.claim.domain.Actor;
import com.chubb.claims.claim.domain.ApprovalLimit;
import com.chubb.claims.claim.domain.Claim;
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
import com.chubb.claims.events.ClaimEventRecorder;
import com.chubb.claims.events.ClaimEventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

@Service
@Transactional
public class ClaimService {

    private static final Logger log = LoggerFactory.getLogger(ClaimService.class);

    private final ClaimRepository claims;
    private final ClaimEventRecorder events;
    private final AuthorityLimits authorityLimits;
    private final Clock clock;

    public ClaimService(ClaimRepository claims, ClaimEventRecorder events, AuthorityLimits authorityLimits,
                        Clock clock) {
        this.claims = claims;
        this.events = events;
        this.authorityLimits = authorityLimits;
        this.clock = clock;
    }

    public ClaimSubmission submit(CurrentUser claimant, ClaimDetails details, String idempotencyKey) {
        if (idempotencyKey != null) {
            Optional<Claim> existing = claims.findByClaimantIdAndIdempotencyKey(claimant.username(), idempotencyKey);
            if (existing.isPresent()) {
                return new ClaimSubmission(toDetail(existing.get(), claimant), false);
            }
        }
        Instant now = clock.instant();
        String claimNumber = ClaimNumber.format(details.market(), now.atZone(ZoneOffset.UTC).getYear(),
                claims.nextClaimNumberSequence());
        Claim claim = claims.save(Claim.submit(claimNumber, claimant.username(), details, idempotencyKey, now));
        events.record(ClaimEventType.CLAIM_SUBMITTED, claim, claimant.username());
        logStatus(claim);
        return new ClaimSubmission(toDetail(claim, claimant), true);
    }

    @Transactional(readOnly = true)
    public PageResponse<ClaimSummaryResponse> findClaims(ClaimStatus status, String assignedOfficerId,
                                                         int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("createdAt"));
        return PageResponse.of(claims.findAll(ClaimSpecifications.matching(status, assignedOfficerId), pageRequest)
                .map(ClaimMapper::toSummary));
    }

    @Transactional(readOnly = true)
    public PageResponse<ClaimSummaryResponse> findClaimsOfClaimant(String claimantId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return PageResponse.of(claims.findByClaimantId(claimantId, pageRequest).map(ClaimMapper::toSummary));
    }

    @Transactional(readOnly = true)
    public ClaimDetailResponse getClaim(UUID claimId, CurrentUser user) {
        return toDetail(loadFor(claimId, user), user);
    }

    public ClaimDetailResponse assign(UUID claimId, CurrentUser officer) {
        return act(claimId, officer, claim -> {
            claim.assignTo(officer.username(), clock.instant());
            return ClaimEventType.CLAIM_ASSIGNED;
        });
    }

    public ClaimDetailResponse startReview(UUID claimId, CurrentUser officer) {
        return act(claimId, officer, claim -> {
            claim.startReview(officer.username(), clock.instant());
            return ClaimEventType.CLAIM_REVIEW_STARTED;
        });
    }

    public ClaimDetailResponse requestInformation(UUID claimId, CurrentUser officer, String question) {
        return act(claimId, officer, claim -> {
            claim.requestInformation(officer.username(), question, clock.instant());
            return ClaimEventType.INFORMATION_REQUESTED;
        });
    }

    public ClaimDetailResponse provideInformation(UUID claimId, UUID requestId, CurrentUser claimant,
                                                  String response) {
        return act(claimId, claimant, claim -> {
            claim.provideInformation(requestId, claimant.username(), response, clock.instant());
            return ClaimEventType.INFORMATION_PROVIDED;
        });
    }

    public ClaimDetailResponse assess(UUID claimId, CurrentUser officer, BigDecimal assessedAmount, String notes) {
        return act(claimId, officer, claim -> {
            claim.assess(officer.username(), assessedAmount, notes, clock.instant());
            return ClaimEventType.CLAIM_ASSESSED;
        });
    }

    public ClaimDetailResponse decide(UUID claimId, CurrentUser officer, Decision.Outcome outcome,
                                      BigDecimal approvedAmount, String reason) {
        return act(claimId, officer, claim -> switch (outcome) {
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
        return act(claimId, approver, claim -> {
            claim.reviewReferral(approver.username(), limitOf(approver, claim), outcome, comment, clock.instant());
            return switch (outcome) {
                case APPROVE -> ClaimEventType.CLAIM_APPROVED;
                case RETURN -> ClaimEventType.REFERRAL_RETURNED;
                case REJECT -> ClaimEventType.CLAIM_REJECTED;
            };
        });
    }

    public ClaimDetailResponse settle(UUID claimId, CurrentUser officer) {
        return act(claimId, officer, claim -> {
            claim.settle(officer.username(), clock.instant());
            return ClaimEventType.CLAIM_SETTLED;
        });
    }

    private ClaimDetailResponse act(UUID claimId, CurrentUser user, Function<Claim, ClaimEventType> command) {
        Claim claim = loadFor(claimId, user);
        ClaimEventType eventType = command.apply(claim);
        events.record(eventType, claim, user.username());
        logStatus(claim);
        return toDetail(claim, user);
    }

    private Claim loadFor(UUID claimId, CurrentUser user) {
        Claim claim = claims.findById(claimId).orElseThrow(() -> new ClaimNotFoundException(claimId));
        if (user.isClaimant() && !claim.getClaimantId().equals(user.username())) {
            throw new ClaimNotFoundException(claimId);
        }
        return claim;
    }

    private ClaimDetailResponse toDetail(Claim claim, CurrentUser user) {
        Actor actor = new Actor(user.username(), user.isOfficer(), limitOf(user, claim));
        return ClaimMapper.toDetail(claim, !user.isClaimant(), claim.availableActions(actor));
    }

    private ApprovalLimit limitOf(CurrentUser user, Claim claim) {
        return authorityLimits.limitFor(user.position(), claim.getCurrency());
    }

    private static void logStatus(Claim claim) {
        log.info("Claim {} is {}", claim.getClaimNumber(), claim.getStatus());
    }
}
