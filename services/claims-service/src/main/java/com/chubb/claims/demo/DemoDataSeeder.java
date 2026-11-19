package com.chubb.claims.demo;

import com.chubb.claims.claim.api.dto.ClaimDetailResponse;
import com.chubb.claims.claim.application.ClaimService;
import com.chubb.claims.claim.domain.ClaimDetails;
import com.chubb.claims.claim.domain.ClaimType;
import com.chubb.claims.claim.domain.Decision;
import com.chubb.claims.claim.domain.Market;
import com.chubb.claims.claim.infrastructure.ClaimRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Component
@ConditionalOnBooleanProperty("app.demo-data.enabled")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final ClaimService claimService;
    private final ClaimRepository claims;

    public DemoDataSeeder(ClaimService claimService, ClaimRepository claims) {
        this.claimService = claimService;
        this.claims = claims;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (claims.count() > 0) {
            return;
        }

        submit("claimant1", Market.SG, ClaimType.MOTOR, "3200.00", "Side mirror and door damaged in car park");
        submit("claimant2", Market.HK, ClaimType.PROPERTY, "48000.00", "Typhoon damage to windows and balcony");

        UUID assigned = submit("claimant1", Market.MY, ClaimType.PROPERTY, "15000.00", "Kitchen fire damage");
        claimService.assign(assigned, "officer1");

        UUID awaitingInfo = submit("claimant2", Market.TH, ClaimType.MOTOR, "52000.00", "Collision on expressway");
        claimService.assign(awaitingInfo, "officer1");
        claimService.startReview(awaitingInfo, "officer1");
        claimService.requestInformation(awaitingInfo, "officer1", "Please provide the police report number.");

        UUID approved = submit("claimant1", Market.ID, ClaimType.MOTOR, "7500000.00", "Rear-ended at traffic light");
        reviewAndAssess(approved, "officer2", "7000000.00");
        claimService.decide(approved, "officer2", Decision.Outcome.APPROVED, new BigDecimal("6800000.00"),
                "Covered under comprehensive policy, less excess");

        UUID rejected = submit("claimant2", Market.PH, ClaimType.PROPERTY, "120000.00", "Water damage from roof leak");
        reviewAndAssess(rejected, "officer2", "0.00");
        claimService.decide(rejected, "officer2", Decision.Outcome.REJECTED, null,
                "Gradual deterioration is excluded under the policy");

        UUID settled = submit("claimant1", Market.SG, ClaimType.PROPERTY, "8800.00", "Burst pipe flooded bathroom");
        reviewAndAssess(settled, "officer3", "8500.00");
        claimService.decide(settled, "officer3", Decision.Outcome.APPROVED, new BigDecimal("8500.00"), "Covered");
        claimService.settle(settled, "officer3");

        log.info("Seeded {} demo claims", claims.count());
    }

    private UUID submit(String claimantId, Market market, ClaimType type, String amount, String description) {
        ClaimDetails details = new ClaimDetails("POL-" + market + "-" + (100000 + claims.count()), market, type,
                LocalDate.now().minusDays(7), description, new BigDecimal(amount));
        ClaimDetailResponse claim = claimService.submit(claimantId, details, null).claim();
        return claim.id();
    }

    private void reviewAndAssess(UUID claimId, String officerId, String reserve) {
        claimService.assign(claimId, officerId);
        claimService.startReview(claimId, officerId);
        claimService.assess(claimId, officerId, new BigDecimal(reserve), "Assessed from submitted documents");
    }
}
