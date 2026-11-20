package com.claimsplatform.claims.claim.application;

import com.claimsplatform.claims.claim.api.dto.ApprovalResponse;
import com.claimsplatform.claims.claim.domain.Actor;
import com.claimsplatform.claims.claim.domain.ApprovalLimit;
import com.claimsplatform.claims.claim.domain.Claim;
import com.claimsplatform.claims.claim.domain.ClaimDetails;
import com.claimsplatform.claims.claim.domain.ClaimType;
import com.claimsplatform.claims.claim.domain.Market;
import com.claimsplatform.claims.claim.domain.Referral;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ApprovalMapperTest {

    private static final Instant NOW = Instant.parse("2026-11-19T10:00:00Z");
    private static final Actor LEAD =
            new Actor("lead1", true, ApprovalLimit.upTo(new BigDecimal("250000")), Set.of("officer1"));

    private static Claim referredClaim() {
        Claim claim = Claim.submit("CLM-SG-2026-000042", "claimant1",
                new ClaimDetails("POL-1", Market.SG, ClaimType.PROPERTY, LocalDate.of(2026, 11, 1), "Fire",
                        new BigDecimal("60000")), null, NOW);
        claim.assignTo("officer1", NOW);
        claim.startReview("officer1", NOW);
        claim.assess("officer1", new BigDecimal("50000"), null, NOW);
        claim.approve("officer1", new BigDecimal("48000"), "Covered",
                ApprovalLimit.upTo(new BigDecimal("10000")), NOW);
        return claim;
    }

    @Test
    void aReferralAwaitingReviewIsPending() {
        ApprovalResponse approval = ApprovalMapper.toApproval(referredClaim(), true);

        assertThat(approval.status()).isEqualTo(ApprovalResponse.Status.PENDING);
        assertThat(approval.recommendedAmount()).isEqualByComparingTo("48000");
        assertThat(approval.reviewedBy()).isNull();
        assertThat(approval.canReview()).isTrue();
    }

    @Test
    void approvedReturnedAndRejectedReferralsShowTheReviewer() {
        Claim approved = referredClaim();
        approved.reviewReferral(LEAD, Referral.Outcome.APPROVE, "Agreed", NOW.plusSeconds(60));
        Claim returned = referredClaim();
        returned.reviewReferral(LEAD, Referral.Outcome.RETURN, "Second quote", NOW.plusSeconds(60));
        Claim rejected = referredClaim();
        rejected.reviewReferral(LEAD, Referral.Outcome.REJECT, "Excluded", NOW.plusSeconds(60));

        assertThat(ApprovalMapper.toApproval(approved, false).status()).isEqualTo(ApprovalResponse.Status.APPROVED);
        assertThat(ApprovalMapper.toApproval(returned, false).status()).isEqualTo(ApprovalResponse.Status.RETURNED);
        assertThat(ApprovalMapper.toApproval(rejected, false).status()).isEqualTo(ApprovalResponse.Status.REJECTED);
        assertThat(ApprovalMapper.toApproval(approved, false).reviewedBy()).isEqualTo("lead1");
    }

    @Test
    void aReturnedReferralThatIsReferredAgainIsPendingAgain() {
        Claim claim = referredClaim();
        claim.reviewReferral(LEAD, Referral.Outcome.RETURN, "Second quote", NOW.plusSeconds(60));
        claim.approve("officer1", new BigDecimal("45000"), "Revised", ApprovalLimit.upTo(new BigDecimal("10000")),
                NOW.plusSeconds(120));

        ApprovalResponse approval = ApprovalMapper.toApproval(claim, true);

        assertThat(approval.status()).isEqualTo(ApprovalResponse.Status.PENDING);
        assertThat(approval.recommendedAmount()).isEqualByComparingTo("45000");
    }
}
