package com.chubb.claims.claim.application;

import com.chubb.claims.claim.api.dto.ClaimDetailResponse;
import com.chubb.claims.claim.api.dto.ClaimSummaryResponse;
import com.chubb.claims.claim.domain.Claim;
import com.chubb.claims.claim.domain.ClaimDetails;
import com.chubb.claims.claim.domain.ClaimNumber;
import com.chubb.claims.claim.domain.ClaimRuleViolationException;
import com.chubb.claims.claim.domain.ClaimStatus;
import com.chubb.claims.claim.domain.Decision;
import com.chubb.claims.claim.infrastructure.ClaimRepository;
import com.chubb.claims.claim.infrastructure.ClaimSpecifications;
import com.chubb.claims.common.security.CurrentUser;
import com.chubb.claims.events.ClaimEventRecorder;
import com.chubb.claims.events.ClaimEventType;
import com.chubb.claims.common.web.PageResponse;
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
import java.util.function.Consumer;

@Service
@Transactional
public class ClaimService {

    private static final Logger log = LoggerFactory.getLogger(ClaimService.class);

    private final ClaimRepository claims;
    private final ClaimEventRecorder events;
    private final Clock clock;

    public ClaimService(ClaimRepository claims, ClaimEventRecorder events, Clock clock) {
        this.claims = claims;
        this.events = events;
        this.clock = clock;
    }

    public ClaimSubmission submit(String claimantId, ClaimDetails details, String idempotencyKey) {
        if (idempotencyKey != null) {
            Optional<Claim> existing = claims.findByClaimantIdAndIdempotencyKey(claimantId, idempotencyKey);
            if (existing.isPresent()) {
                return new ClaimSubmission(ClaimMapper.toDetail(existing.get(), false), false);
            }
        }
        Instant now = clock.instant();
        String claimNumber = ClaimNumber.format(details.market(), now.atZone(ZoneOffset.UTC).getYear(),
                claims.nextClaimNumberSequence());
        Claim claim = claims.save(Claim.submit(claimNumber, claimantId, details, idempotencyKey, now));
        events.record(ClaimEventType.CLAIM_SUBMITTED, claim, claimantId);
        logStatus(claim);
        return new ClaimSubmission(ClaimMapper.toDetail(claim, false), true);
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
        Claim claim = user.isClaimant() ? loadOwnedBy(claimId, user.username()) : load(claimId);
        return ClaimMapper.toDetail(claim, !user.isClaimant());
    }

    public ClaimDetailResponse assign(UUID claimId, String officerId) {
        return officerAction(claimId, officerId, ClaimEventType.CLAIM_ASSIGNED,
                claim -> claim.assignTo(officerId, clock.instant()));
    }

    public ClaimDetailResponse startReview(UUID claimId, String officerId) {
        return officerAction(claimId, officerId, ClaimEventType.CLAIM_REVIEW_STARTED,
                claim -> claim.startReview(officerId, clock.instant()));
    }

    public ClaimDetailResponse requestInformation(UUID claimId, String officerId, String question) {
        return officerAction(claimId, officerId, ClaimEventType.INFORMATION_REQUESTED,
                claim -> claim.requestInformation(officerId, question, clock.instant()));
    }

    public ClaimDetailResponse provideInformation(UUID claimId, UUID requestId, String claimantId, String response) {
        Claim claim = loadOwnedBy(claimId, claimantId);
        claim.provideInformation(requestId, claimantId, response, clock.instant());
        events.record(ClaimEventType.INFORMATION_PROVIDED, claim, claimantId);
        logStatus(claim);
        return ClaimMapper.toDetail(claim, false);
    }

    public ClaimDetailResponse assess(UUID claimId, String officerId, BigDecimal assessedAmount, String notes) {
        return officerAction(claimId, officerId, ClaimEventType.CLAIM_ASSESSED,
                claim -> claim.assess(officerId, assessedAmount, notes, clock.instant()));
    }

    public ClaimDetailResponse decide(UUID claimId, String officerId, Decision.Outcome outcome,
                                      BigDecimal approvedAmount, String reason) {
        ClaimEventType eventType = switch (outcome) {
            case APPROVED -> ClaimEventType.CLAIM_APPROVED;
            case REJECTED -> ClaimEventType.CLAIM_REJECTED;
        };
        return officerAction(claimId, officerId, eventType, claim -> {
            switch (outcome) {
                case APPROVED -> claim.approve(officerId, approvedAmount, reason, clock.instant());
                case REJECTED -> {
                    if (approvedAmount != null) {
                        throw new ClaimRuleViolationException("approvedAmount must not be set when rejecting a claim");
                    }
                    claim.reject(officerId, reason, clock.instant());
                }
            }
        });
    }

    public ClaimDetailResponse settle(UUID claimId, String officerId) {
        return officerAction(claimId, officerId, ClaimEventType.CLAIM_SETTLED,
                claim -> claim.settle(officerId, clock.instant()));
    }

    private ClaimDetailResponse officerAction(UUID claimId, String officerId, ClaimEventType eventType,
                                              Consumer<Claim> action) {
        Claim claim = load(claimId);
        action.accept(claim);
        events.record(eventType, claim, officerId);
        logStatus(claim);
        return ClaimMapper.toDetail(claim, true);
    }

    private Claim load(UUID claimId) {
        return claims.findById(claimId).orElseThrow(() -> new ClaimNotFoundException(claimId));
    }

    private Claim loadOwnedBy(UUID claimId, String claimantId) {
        Claim claim = load(claimId);
        if (!claim.getClaimantId().equals(claimantId)) {
            throw new ClaimNotFoundException(claimId);
        }
        return claim;
    }

    private static void logStatus(Claim claim) {
        log.info("Claim {} is {}", claim.getClaimNumber(), claim.getStatus());
    }
}
