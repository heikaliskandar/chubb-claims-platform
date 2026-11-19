package com.chubb.claims.claim.infrastructure;

import com.chubb.claims.TestcontainersConfiguration;
import com.chubb.claims.claim.domain.Claim;
import com.chubb.claims.claim.domain.ClaimDetails;
import com.chubb.claims.claim.domain.ClaimStatus;
import com.chubb.claims.claim.domain.ClaimType;
import com.chubb.claims.claim.domain.Decision;
import com.chubb.claims.claim.domain.InformationRequest;
import com.chubb.claims.claim.domain.Market;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ClaimRepositoryIT {

    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MICROS);

    @Autowired
    private ClaimRepository claims;

    @Autowired
    private TransactionTemplate transaction;

    @Test
    void persistsTheWholeClaimAggregateAgainstTheFlywaySchema() {
        UUID claimId = transaction.execute(status -> {
            Claim claim = Claim.submit("CLM-SG-2026-000001", "claimant1",
                    new ClaimDetails("POL-SG-1001", Market.SG, ClaimType.PROPERTY,
                            LocalDate.of(2026, 10, 30), "Burst pipe flooded the kitchen", new BigDecimal("12500.50")),
                    "key-it-1", NOW);
            claim.assignTo("officer1", NOW);
            claim.startReview("officer1", NOW);
            InformationRequest request = claim.requestInformation("officer1", "Plumber's report?", NOW);
            claim.provideInformation(request.getId(), "claimant1", "Attached", NOW);
            claim.assess("officer1", new BigDecimal("11000.00"), "Loss adjuster estimate", NOW);
            claim.approve("officer1", new BigDecimal("10800.00"), "Covered", NOW);
            return claims.save(claim).getId();
        });

        transaction.executeWithoutResult(status -> {
            Claim reloaded = claims.findById(claimId).orElseThrow();

            assertThat(reloaded.getStatus()).isEqualTo(ClaimStatus.APPROVED);
            assertThat(reloaded.getCurrency()).isEqualTo("SGD");
            assertThat(reloaded.getClaimedAmount()).hasToString("12500.50");
            assertThat(reloaded.getReserveAmount()).hasToString("10800.00");
            assertThat(reloaded.getDecision().outcome()).isEqualTo(Decision.Outcome.APPROVED);
            assertThat(reloaded.getDecision().decidedAt()).isEqualTo(NOW);
            assertThat(reloaded.getStatusHistory()).hasSize(6);
            assertThat(reloaded.getInformationRequests()).singleElement()
                    .satisfies(request -> assertThat(request.getResponse()).isEqualTo("Attached"));
            assertThat(reloaded.getAssessments()).hasSize(1);
            assertThat(reloaded.getVersion()).isZero();
        });
    }

    @Test
    void claimNumberSequenceNeverRepeats() {
        long first = claims.nextClaimNumberSequence();
        long second = claims.nextClaimNumberSequence();

        assertThat(second).isGreaterThan(first);
    }
}
