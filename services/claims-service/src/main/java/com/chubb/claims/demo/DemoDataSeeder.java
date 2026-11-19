package com.chubb.claims.demo;

import com.chubb.claims.claim.application.ClaimService;
import com.chubb.claims.claim.domain.ClaimDetails;
import com.chubb.claims.claim.domain.ClaimType;
import com.chubb.claims.claim.domain.Decision;
import com.chubb.claims.claim.domain.Market;
import com.chubb.claims.claim.infrastructure.ClaimRepository;
import com.chubb.claims.common.security.CurrentUser;
import com.chubb.claims.common.security.Position;
import com.chubb.claims.common.security.Role;
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

    private static final CurrentUser CLAIMANT_1 = new CurrentUser("claimant1", Role.CLAIMANT, null);
    private static final CurrentUser CLAIMANT_2 = new CurrentUser("claimant2", Role.CLAIMANT, null);
    private static final CurrentUser OFFICER_1 = new CurrentUser("officer1", Role.OFFICER, Position.CLAIMS_OFFICER);
    private static final CurrentUser OFFICER_2 =
            new CurrentUser("officer2", Role.OFFICER, Position.SENIOR_CLAIMS_OFFICER);
    private static final CurrentUser OFFICER_3 = new CurrentUser("officer3", Role.OFFICER, Position.CLAIMS_OFFICER);

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

        submit(CLAIMANT_1, Market.SG, ClaimType.MOTOR, "3200.00", "Side mirror and door damaged in car park");
        submit(CLAIMANT_2, Market.HK, ClaimType.PROPERTY, "48000.00", "Typhoon damage to windows and balcony");

        UUID assigned = submit(CLAIMANT_1, Market.MY, ClaimType.PROPERTY, "15000.00", "Kitchen fire damage");
        claimService.assign(assigned, OFFICER_1);

        UUID awaitingInfo = submit(CLAIMANT_2, Market.TH, ClaimType.MOTOR, "52000.00", "Collision on expressway");
        claimService.assign(awaitingInfo, OFFICER_1);
        claimService.startReview(awaitingInfo, OFFICER_1);
        claimService.requestInformation(awaitingInfo, OFFICER_1, "Please provide the police report number.");

        UUID referred = submit(CLAIMANT_2, Market.SG, ClaimType.PROPERTY, "85000.00", "Fire spread from neighbour");
        reviewAndAssess(referred, OFFICER_1, "80000.00");
        claimService.decide(referred, OFFICER_1, Decision.Outcome.APPROVED, new BigDecimal("78000.00"),
                "Covered; loss adjuster report supports the amount");

        UUID approved = submit(CLAIMANT_1, Market.ID, ClaimType.MOTOR, "7500000.00", "Rear-ended at traffic light");
        reviewAndAssess(approved, OFFICER_2, "7000000.00");
        claimService.decide(approved, OFFICER_2, Decision.Outcome.APPROVED, new BigDecimal("6800000.00"),
                "Covered under comprehensive policy, less excess");

        UUID rejected = submit(CLAIMANT_2, Market.PH, ClaimType.PROPERTY, "120000.00", "Water damage from roof leak");
        reviewAndAssess(rejected, OFFICER_2, "0.00");
        claimService.decide(rejected, OFFICER_2, Decision.Outcome.REJECTED, null,
                "Gradual deterioration is excluded under the policy");

        UUID settled = submit(CLAIMANT_1, Market.SG, ClaimType.PROPERTY, "8800.00", "Burst pipe flooded bathroom");
        reviewAndAssess(settled, OFFICER_3, "8500.00");
        claimService.decide(settled, OFFICER_3, Decision.Outcome.APPROVED, new BigDecimal("8500.00"), "Covered");
        claimService.settle(settled, OFFICER_3);

        log.info("Seeded {} demo claims", claims.count());
    }

    private UUID submit(CurrentUser claimant, Market market, ClaimType type, String amount, String description) {
        ClaimDetails details = new ClaimDetails("POL-" + market + "-" + (100000 + claims.count()), market, type,
                LocalDate.now().minusDays(7), description, new BigDecimal(amount));
        return claimService.submit(claimant, details, null).claim().id();
    }

    private void reviewAndAssess(UUID claimId, CurrentUser officer, String reserve) {
        claimService.assign(claimId, officer);
        claimService.startReview(claimId, officer);
        claimService.assess(claimId, officer, new BigDecimal(reserve), "Assessed from submitted documents");
    }
}
