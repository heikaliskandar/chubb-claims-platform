package com.chubb.claims.claim.domain;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static com.chubb.claims.claim.domain.ClaimStatus.APPROVED;
import static com.chubb.claims.claim.domain.ClaimStatus.ASSIGNED;
import static com.chubb.claims.claim.domain.ClaimStatus.INFO_REQUESTED;
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
            claim.approve(OFFICER, new BigDecimal("4000.00"), "Covered under policy section 2", NOW);
            claim.settle(OFFICER, NOW);

            assertThat(claim.getStatus()).isEqualTo(SETTLED);
            assertThat(claim.getStatusHistory()).extracting(ClaimStatusHistory::getToStatus)
                    .containsExactly(SUBMITTED, ASSIGNED, UNDER_REVIEW, INFO_REQUESTED, UNDER_REVIEW, APPROVED, SETTLED);
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

            assertThatThrownBy(() -> claim.approve(OFFICER, new BigDecimal("1000.00"), "OK", NOW))
                    .isInstanceOf(ClaimRuleViolationException.class)
                    .hasMessageContaining("assessed");
        }

        @Test
        void approvedAmountCannotExceedClaimedAmount() {
            Claim claim = assessedClaim("5000.00");

            assertThatThrownBy(() -> claim.approve(OFFICER, new BigDecimal("5000.01"), "OK", NOW))
                    .isInstanceOf(ClaimRuleViolationException.class);
        }

        @Test
        void approvingTheFullClaimedAmountSetsReserveToTheApprovedAmount() {
            Claim claim = assessedClaim("4800.00");

            claim.approve(OFFICER, new BigDecimal("5000"), "Total loss", NOW);

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

            assertThatThrownBy(() -> claim.approve(OFFICER, new BigDecimal("100.00"), "Changed my mind", NOW))
                    .isInstanceOf(InvalidClaimStateException.class);
        }
    }

    @Test
    void claimNumberIsZeroPaddedToSixDigits() {
        assertThat(ClaimNumber.format(Market.MY, 2026, 123)).isEqualTo("CLM-MY-2026-000123");
        assertThat(ClaimNumber.format(Market.SG, 2026, 1_234_567)).isEqualTo("CLM-SG-2026-1234567");
    }
}
