package com.claimsplatform.claims.claim.application;

import com.claimsplatform.claims.TestcontainersConfiguration;
import com.claimsplatform.claims.claim.domain.Claim;
import com.claimsplatform.claims.claim.domain.ClaimDetails;
import com.claimsplatform.claims.claim.domain.ClaimType;
import com.claimsplatform.claims.claim.domain.Market;
import com.claimsplatform.claims.claim.infrastructure.ClaimRepository;
import com.claimsplatform.claims.common.security.CurrentUser;
import com.claimsplatform.claims.common.security.Position;
import com.claimsplatform.claims.common.security.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "app.security.demo-users.password=test-password")
@Import(TestcontainersConfiguration.class)
class OptimisticLockingIT {

    @Autowired
    private ClaimService claimService;

    @Autowired
    private ClaimRepository claims;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void aStaleCopyOfTheClaimCannotOverwriteAnotherOfficersChange() {
        ClaimDetails details = new ClaimDetails("POL-HK-2207", Market.HK, ClaimType.PROPERTY,
                LocalDate.of(2026, 11, 5), "Ceiling collapse after heavy rain", new BigDecimal("18000.00"));
        CurrentUser claimant = new CurrentUser("claimant2", Role.CLAIMANT, null);
        CurrentUser officer = new CurrentUser("officer1", Role.OFFICER, Position.CLAIMS_OFFICER);
        UUID claimId = claimService.submit(claimant, details, null).claim().id();

        TransactionTemplate officerTwo = new TransactionTemplate(transactionManager);
        TransactionTemplate officerOne = new TransactionTemplate(transactionManager);
        officerOne.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        assertThatThrownBy(() -> officerTwo.executeWithoutResult(status -> {
            Claim staleCopy = claims.findById(claimId).orElseThrow();
            officerOne.executeWithoutResult(inner -> claimService.assign(claimId, officer));
            staleCopy.assignTo("officer2", Instant.now());
        })).isInstanceOf(OptimisticLockingFailureException.class);

        assertThat(claims.findById(claimId).orElseThrow().getAssignedOfficerId()).isEqualTo("officer1");
    }
}
