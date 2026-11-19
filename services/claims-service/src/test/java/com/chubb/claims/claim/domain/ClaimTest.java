package com.chubb.claims.claim.domain;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import static com.chubb.claims.claim.domain.ClaimStatus.APPROVED;
import static com.chubb.claims.claim.domain.ClaimStatus.ASSIGNED;
import static com.chubb.claims.claim.domain.ClaimStatus.INFO_REQUESTED;
import static com.chubb.claims.claim.domain.ClaimStatus.PENDING_APPROVAL;
import static com.chubb.claims.claim.domain.ClaimStatus.REJECTED;
import static com.chubb.claims.claim.domain.ClaimStatus.SETTLED;
import static com.chubb.claims.claim.domain.ClaimStatus.SUBMITTED;
import static com.chubb.claims.claim.domain.ClaimStatus.UNDER_REVIEW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClaimTest {

    private static final Instant NOW = Instant.parse("2026-11-19T10:00:00Z");
    private static final String CLAIMANT = "claimant1";
    private static final String OFFICER = "officer1";
    private static final String OTHER_OFFICER = "officer2";
    private static final String TEAM_LEAD = "lead1";
    private static final ApprovalLimit ANY_AMOUNT = ApprovalLimit.UNLIMITED;
    private static final ApprovalLimit UP_TO_10K = ApprovalLimit.upTo(new BigDecimal("10000"));
    private static final ApprovalLimit UP_TO_100K = ApprovalLimit.upTo(new BigDecimal("100000"));
    private static final Actor LEAD_OF_OFFICERS =
            new Actor(TEAM_LEAD, true, UP_TO_100K, Set.of(OFFICER, OTHER_OFFICER));

    private static Claim submittedClaim(String claimedAmount) {
        ClaimDetails details = new ClaimDetails("POL-MY-778812", Market.MY, ClaimType.MOTOR,
                LocalDate.of(2026, 11, 1), "Rear-ended at a traffic light", new BigDecimal(claimedAmount));
        return Claim.submit("CLM-MY-2026-000001", CLAIMANT, details, "key-1", NOW);
    }

    private static Claim submittedClaim() {
        return submittedClaim("5000.00");
    }

    private static Claim claimUnderReview() {
        Claim claim = submittedClaim();
        claim.assignTo(OFFICER, NOW);
        claim.startReview(OFFICER, NOW);
        return claim;
    }

    private static Claim assessedClaim(String reserve) {
        Claim claim = claimUnderReview();
        claim.assess(OFFICER, new BigDecimal(reserve), "Workshop quote received", NOW);
        return claim;
    }

    @Nested
    class Submission {

        @Test
        void startsSubmittedWithAnAuditEntryAndTheMarketCurrency() {
            Claim claim = submittedClaim();

            assertThat(claim.getStatus()).isEqualTo(SUBMITTED);
            assertThat(claim.getCurrency()).isEqualTo("MYR");
            assertThat(claim.getReserveAmount()).isEqualByComparingTo("0");
            assertThat(claim.getStatusHistory()).singleElement().satisfies(entry -> {
                assertThat(entry.getFromStatus()).isNull();
                assertThat(entry.getToStatus()).isEqualTo(SUBMITTED);
                assertThat(entry.getChangedBy()).isEqualTo(CLAIMANT);
            });
        }

        @ParameterizedTest
        @ValueSource(strings = {"0", "0.00", "-1"})
        void rejectsAClaimedAmountThatIsNotPositive(String amount) {
            assertThatThrownBy(() -> submittedClaim(amount))
                    .isInstanceOf(ClaimRuleViolationException.class);
        }

        @Test
        void rejectsMoreThanTwoDecimalPlacesInsteadOfRounding() {
            assertThatThrownBy(() -> submittedClaim("100.005"))
                    .isInstanceOf(ClaimRuleViolationException.class)
                    .hasMessageContaining("decimal places");
        }

        @Test
        void normalisesAmountsToTwoDecimalPlaces() {
            assertThat(submittedClaim("100.500").getClaimedAmount()).hasToString("100.50");
            assertThat(submittedClaim("100").getClaimedAmount()).hasToString("100.00");
        }
    }

    @Nested
    class Lifecycle {

        @Test
        void approvedClaimIsSettledWithAHistoryEntryForEveryTransition() {
            Claim claim = claimUnderReview();
            InformationRequest request = claim.requestInformation(OFFICER, "Please upload the police report", NOW);
            claim.provideInformation(request.getId(), CLAIMANT, "Report number PR-4471", NOW);
            claim.assess(OFFICER, new BigDecimal("4200.00"), "Workshop quote received", NOW);
            claim.approve(OFFICER, new BigDecimal("4000.00"), "Covered under policy section 2", ANY_AMOUNT, NOW);
            claim.settle(OFFICER, NOW);

            assertThat(claim.getStatus()).isEqualTo(SETTLED);
            assertThat(claim.getStatusHistory()).extracting(ClaimStatusHistory::getToStatus)
                    .containsExactly(SUBMITTED, ASSIGNED, UNDER_REVIEW, INFO_REQUESTED, UNDER_REVIEW, APPROVED,
                            SETTLED);
            assertThat(claim.getDecision().outcome()).isEqualTo(Decision.Outcome.APPROVED);
            assertThat(claim.getDecision().approvedAmount()).isEqualByComparingTo("4000.00");
            assertThat(claim.getReserveAmount()).isEqualByComparingTo("0");
        }

        @Test
        void assigningRecordsTheOfficer() {
            Claim claim = submittedClaim();

            claim.assignTo(OFFICER, NOW);

            assertThat(claim.getStatus()).isEqualTo(ASSIGNED);
            assertThat(claim.getAssignedOfficerId()).isEqualTo(OFFICER);
        }

        @Test
        void anAssignedClaimCannotBePickedUpAgain() {
            Claim claim = submittedClaim();
            claim.assignTo(OFFICER, NOW);

            assertThatThrownBy(() -> claim.assignTo(OTHER_OFFICER, NOW))
                    .isInstanceOf(InvalidClaimStateException.class);
            assertThat(claim.getAssignedOfficerId()).isEqualTo(OFFICER);
        }

        @Test
        void aClaimCannotBeSettledBeforeItIsApproved() {
            Claim claim = claimUnderReview();

            assertThatThrownBy(() -> claim.settle(OFFICER, NOW))
                    .isInstanceOf(InvalidClaimStateException.class)
                    .hasMessage("Cannot move claim from UNDER_REVIEW to SETTLED");
        }

        @Test
        void onlyTheAssignedOfficerCanActOnTheClaim() {
            Claim claim = claimUnderReview();

            assertThatThrownBy(() -> claim.requestInformation(OTHER_OFFICER, "Any photos?", NOW))
                    .isInstanceOf(ClaimAccessDeniedException.class);
            assertThat(claim.getStatus()).isEqualTo(UNDER_REVIEW);
        }
    }

    @Nested
    class InformationRequests {

        @Test
        void requestingInformationOpensARequestAndWaitsForTheClaimant() {
            Claim claim = claimUnderReview();

            InformationRequest request = claim.requestInformation(OFFICER, "Please upload photos", NOW);

            assertThat(claim.getStatus()).isEqualTo(INFO_REQUESTED);
            assertThat(request.isOpen()).isTrue();
            assertThat(request.getRequestedBy()).isEqualTo(OFFICER);
        }

        @Test
        void answeringReturnsTheClaimToReview() {
            Claim claim = claimUnderReview();
            InformationRequest request = claim.requestInformation(OFFICER, "Please upload photos", NOW);

            claim.provideInformation(request.getId(), CLAIMANT, "Photos attached", NOW);

            assertThat(claim.getStatus()).isEqualTo(UNDER_REVIEW);
            assertThat(request.getStatus()).isEqualTo(InformationRequest.Status.ANSWERED);
            assertThat(request.getResponse()).isEqualTo("Photos attached");
        }

        @Test
        void onlyTheClaimantCanAnswer() {
            Claim claim = claimUnderReview();
            InformationRequest request = claim.requestInformation(OFFICER, "Please upload photos", NOW);

            assertThatThrownBy(() -> claim.provideInformation(request.getId(), "claimant2", "Not mine", NOW))
                    .isInstanceOf(ClaimAccessDeniedException.class);
            assertThat(request.isOpen()).isTrue();
        }

        @Test
        void anAnsweredRequestCannotBeAnsweredAgain() {
            Claim claim = claimUnderReview();
            InformationRequest first = claim.requestInformation(OFFICER, "Please upload photos", NOW);
            claim.provideInformation(first.getId(), CLAIMANT, "Photos attached", NOW);
            claim.requestInformation(OFFICER, "Please upload the police report", NOW);

            assertThatThrownBy(() -> claim.provideInformation(first.getId(), CLAIMANT, "Again", NOW))
                    .isInstanceOf(ClaimRuleViolationException.class);
            assertThat(claim.getStatus()).isEqualTo(INFO_REQUESTED);
        }

        @Test
        void answeringAnUnknownRequestFails() {
            Claim claim = claimUnderReview();
            claim.requestInformation(OFFICER, "Please upload photos", NOW);

            assertThatThrownBy(() -> claim.provideInformation(UUID.randomUUID(), CLAIMANT, "Photos", NOW))
                    .isInstanceOf(InformationRequestNotFoundException.class);
        }
    }

    @Nested
    class Assessments {

        @Test
        void assessingSetsTheReserveWithoutChangingStatus() {
            Claim claim = assessedClaim("4200.00");

            assertThat(claim.getStatus()).isEqualTo(UNDER_REVIEW);
            assertThat(claim.getReserveAmount()).isEqualByComparingTo("4200.00");
            assertThat(claim.getAssessments()).singleElement()
                    .satisfies(assessment -> assertThat(assessment.getCurrency()).isEqualTo("MYR"));
        }

        @Test
        void reassessingKeepsEveryAssessmentAndUsesTheLatestAsReserve() {
            Claim claim = assessedClaim("4200.00");

            claim.assess(OFFICER, new BigDecimal("3900.00"), "Revised quote", NOW);

            assertThat(claim.getAssessments()).hasSize(2);
            assertThat(claim.getReserveAmount()).isEqualByComparingTo("3900.00");
        }

        @Test
        void aClaimCanOnlyBeAssessedWhileUnderReview() {
            Claim claim = submittedClaim();
            claim.assignTo(OFFICER, NOW);

            assertThatThrownBy(() -> claim.assess(OFFICER, new BigDecimal("100.00"), null, NOW))
                    .isInstanceOf(InvalidClaimStateException.class);
        }
    }

    @Nested
    class Decisions {

        @Test
        void approvalRequiresAnAssessment() {
            Claim claim = claimUnderReview();

            assertThatThrownBy(() -> claim.approve(OFFICER, new BigDecimal("1000.00"), "OK", ANY_AMOUNT, NOW))
                    .isInstanceOf(ClaimRuleViolationException.class)
                    .hasMessageContaining("assessed");
        }

        @Test
        void approvedAmountCannotExceedClaimedAmount() {
            Claim claim = assessedClaim("5000.00");

            assertThatThrownBy(() -> claim.approve(OFFICER, new BigDecimal("5000.01"), "OK", ANY_AMOUNT, NOW))
                    .isInstanceOf(ClaimRuleViolationException.class);
        }

        @Test
        void approvingTheFullClaimedAmountSetsReserveToTheApprovedAmount() {
            Claim claim = assessedClaim("4800.00");

            claim.approve(OFFICER, new BigDecimal("5000"), "Total loss", ANY_AMOUNT, NOW);

            assertThat(claim.getStatus()).isEqualTo(APPROVED);
            assertThat(claim.getReserveAmount()).isEqualByComparingTo("5000.00");
            assertThat(claim.getStatus().isTerminal()).isFalse();
        }

        @Test
        void rejectingRecordsTheReasonAndReleasesTheReserve() {
            Claim claim = assessedClaim("4200.00");

            claim.reject(OFFICER, "Policy lapsed before incident date", NOW);

            assertThat(claim.getStatus()).isEqualTo(REJECTED);
            assertThat(claim.getReserveAmount()).isEqualByComparingTo("0");
            assertThat(claim.getDecision().outcome()).isEqualTo(Decision.Outcome.REJECTED);
            assertThat(claim.getDecision().approvedAmount()).isNull();
            assertThat(claim.getStatusHistory().getLast().getReason()).isEqualTo("Policy lapsed before incident date");
        }

        @Test
        void rejectionRequiresAReason() {
            Claim claim = claimUnderReview();

            assertThatThrownBy(() -> claim.reject(OFFICER, " ", NOW))
                    .isInstanceOf(ClaimRuleViolationException.class);
            assertThat(claim.getStatus()).isEqualTo(UNDER_REVIEW);
        }

        @Test
        void aRejectedClaimCannotBeApproved() {
            Claim claim = assessedClaim("4200.00");
            claim.reject(OFFICER, "Not covered", NOW);

            assertThatThrownBy(() ->
                    claim.approve(OFFICER, new BigDecimal("100.00"), "Changed my mind", ANY_AMOUNT, NOW))
                    .isInstanceOf(InvalidClaimStateException.class);
        }
    }

    private static Claim referredClaim() {
        Claim claim = submittedClaim("60000.00");
        claim.assignTo(OFFICER, NOW);
        claim.startReview(OFFICER, NOW);
        claim.assess(OFFICER, new BigDecimal("50000.00"), "Loss adjuster estimate", NOW);
        claim.approve(OFFICER, new BigDecimal("48000.00"), "Covered", UP_TO_10K, NOW);
        return claim;
    }

    @Nested
    class DelegatedAuthority {

        @Test
        void anApprovalWithinTheOfficersLimitIsFinal() {
            Claim claim = assessedClaim("4200.00");

            claim.approve(OFFICER, new BigDecimal("4000.00"), "Covered", UP_TO_10K, NOW);

            assertThat(claim.getStatus()).isEqualTo(APPROVED);
            assertThat(claim.getDecision().decidedBy()).isEqualTo(OFFICER);
        }

        @Test
        void anApprovalAboveTheOfficersLimitIsReferredWithoutADecision() {
            Claim claim = referredClaim();

            assertThat(claim.getStatus()).isEqualTo(PENDING_APPROVAL);
            assertThat(claim.getDecision()).isNull();
            assertThat(claim.getReferral().referredBy()).isEqualTo(OFFICER);
            assertThat(claim.getReferral().amount()).isEqualByComparingTo("48000.00");
            assertThat(claim.getReserveAmount()).isEqualByComparingTo("50000.00");
        }

        @Test
        void anApproverWithEnoughAuthorityApprovesTheReferredAmount() {
            Claim claim = referredClaim();

            claim.reviewReferral(LEAD_OF_OFFICERS, Referral.Outcome.APPROVE, "Agreed", NOW);

            assertThat(claim.getStatus()).isEqualTo(APPROVED);
            assertThat(claim.getDecision().decidedBy()).isEqualTo(TEAM_LEAD);
            assertThat(claim.getDecision().approvedAmount()).isEqualByComparingTo("48000.00");
            assertThat(claim.getReserveAmount()).isEqualByComparingTo("48000.00");
        }

        @Test
        void fourEyesRuleStopsTheRecommenderApprovingTheirOwnReferral() {
            Claim claim = referredClaim();

            Actor recommender = new Actor(OFFICER, true, ANY_AMOUNT, Set.of());

            assertThatThrownBy(() -> claim.reviewReferral(recommender, Referral.Outcome.APPROVE, null, NOW))
                    .isInstanceOf(ClaimAccessDeniedException.class)
                    .hasMessageContaining("Four-eyes");
        }

        @Test
        void onlySomeoneAboveTheRecommenderInTheReportingLineCanReview() {
            Claim claim = referredClaim();
            Actor leadOfAnotherTeam = new Actor("lead2", true, ANY_AMOUNT, Set.of("officer3"));

            assertThatThrownBy(() -> claim.reviewReferral(leadOfAnotherTeam, Referral.Outcome.APPROVE, null, NOW))
                    .isInstanceOf(ClaimAccessDeniedException.class)
                    .hasMessageContaining("manager above");
            assertThat(claim.getStatus()).isEqualTo(PENDING_APPROVAL);
        }

        @Test
        void aManagerAboveWithoutEnoughAuthorityCannotApprove() {
            Claim claim = referredClaim();
            Actor leadWithLowLimit = new Actor(TEAM_LEAD, true, UP_TO_10K, Set.of(OFFICER));

            assertThatThrownBy(() -> claim.reviewReferral(leadWithLowLimit, Referral.Outcome.APPROVE, null, NOW))
                    .isInstanceOf(ClaimAccessDeniedException.class)
                    .hasMessageContaining("authority");
            assertThat(claim.getStatus()).isEqualTo(PENDING_APPROVAL);
        }

        @Test
        void returningAReferralSendsTheClaimBackToReviewWithTheApproversComment() {
            Claim claim = referredClaim();

            claim.reviewReferral(LEAD_OF_OFFICERS, Referral.Outcome.RETURN, "Get a second quote", NOW);

            assertThat(claim.getStatus()).isEqualTo(UNDER_REVIEW);
            assertThat(claim.getStatusHistory().getLast().getReason()).isEqualTo("Get a second quote");
        }

        @Test
        void anApproverCanRejectAReferral() {
            Claim claim = referredClaim();

            claim.reviewReferral(LEAD_OF_OFFICERS, Referral.Outcome.REJECT, "Excluded peril", NOW);

            assertThat(claim.getStatus()).isEqualTo(REJECTED);
            assertThat(claim.getDecision().decidedBy()).isEqualTo(TEAM_LEAD);
            assertThat(claim.getReserveAmount()).isEqualByComparingTo("0");
        }

        @Test
        void theAssignedOfficerCannotBypassTheApproverWhileTheClaimIsReferred() {
            Claim claim = referredClaim();

            assertThatThrownBy(() -> claim.approve(OFFICER, new BigDecimal("5000.00"), "Lower", UP_TO_10K, NOW))
                    .isInstanceOf(InvalidClaimStateException.class);
            assertThatThrownBy(() -> claim.reject(OFFICER, "Changed my mind", NOW))
                    .isInstanceOf(InvalidClaimStateException.class);
        }
    }

    @Nested
    class AvailableActions {

        private final Actor officer = new Actor(OFFICER, true, UP_TO_10K, Set.of());
        private final Actor otherOfficer = new Actor(OTHER_OFFICER, true, UP_TO_10K, Set.of());
        private final Actor teamLead = LEAD_OF_OFFICERS;
        private final Actor leadOfAnotherTeam = new Actor("lead2", true, UP_TO_100K, Set.of("officer3"));
        private final Actor claimant = new Actor(CLAIMANT, false, ApprovalLimit.NONE, Set.of());

        @Test
        void anyOfficerCanPickUpASubmittedClaimButTheClaimantCannot() {
            Claim claim = submittedClaim();

            assertThat(claim.availableActions(otherOfficer)).containsExactly(ClaimAction.ASSIGN_TO_ME);
            assertThat(claim.availableActions(claimant)).isEmpty();
        }

        @Test
        void onlyTheAssignedOfficerSeesReviewActionsAndApproveOnlyAfterAssessment() {
            Claim claim = claimUnderReview();

            assertThat(claim.availableActions(officer)).containsExactlyInAnyOrder(
                    ClaimAction.REQUEST_INFORMATION, ClaimAction.ASSESS, ClaimAction.REJECT);
            assertThat(claim.availableActions(otherOfficer)).isEmpty();

            claim.assess(OFFICER, new BigDecimal("1000.00"), null, NOW);
            assertThat(claim.availableActions(officer)).contains(ClaimAction.APPROVE);
        }

        @Test
        void theClaimantCanRespondOnlyWhileInformationIsRequested() {
            Claim claim = claimUnderReview();
            claim.requestInformation(OFFICER, "Photos?", NOW);

            assertThat(claim.availableActions(claimant)).containsExactly(ClaimAction.RESPOND_TO_INFORMATION_REQUEST);
            assertThat(claim.availableActions(officer)).isEmpty();
        }

        @Test
        void aReferralCanOnlyBeReviewedBySomeoneElseWithEnoughAuthority() {
            Claim claim = referredClaim();

            assertThat(claim.availableActions(teamLead)).containsExactlyInAnyOrder(
                    ClaimAction.APPROVE_REFERRAL, ClaimAction.RETURN_REFERRAL, ClaimAction.REJECT_REFERRAL);
            assertThat(claim.availableActions(officer)).isEmpty();
            assertThat(claim.availableActions(otherOfficer)).isEmpty();
            assertThat(claim.availableActions(leadOfAnotherTeam)).isEmpty();
        }

        @Test
        void eachStatusSaysWhoTheClaimIsWaitingOn() {
            assertThat(submittedClaim().getStatus().waitingOn()).isEqualTo(ClaimStatus.Party.CLAIMS_TEAM);
            assertThat(referredClaim().getStatus().waitingOn()).isEqualTo(ClaimStatus.Party.APPROVER);
        }
    }

    @Test
    void claimNumberIsZeroPaddedToSixDigits() {
        assertThat(ClaimNumber.format(Market.MY, 2026, 123)).isEqualTo("CLM-MY-2026-000123");
        assertThat(ClaimNumber.format(Market.SG, 2026, 1_234_567)).isEqualTo("CLM-SG-2026-1234567");
    }
}
