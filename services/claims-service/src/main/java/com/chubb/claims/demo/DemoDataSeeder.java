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
import com.chubb.claims.employee.domain.Employee;
import com.chubb.claims.employee.infrastructure.EmployeeRepository;
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
    private final EmployeeRepository employees;

    public DemoDataSeeder(ClaimService claimService, ClaimRepository claims, EmployeeRepository employees) {
        this.claimService = claimService;
        this.claims = claims;
        this.employees = employees;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (employees.count() == 0) {
            seedEmployees();
        }
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

    private void seedEmployees() {
        employees.save(employee("manager1", "EMP-0001", "Grace Tan", Position.CLAIMS_MANAGER, "Claims Leadership",
                Market.SG, "Singapore HQ", null, "2015-03-02"));
        employees.save(employee("lead1", "EMP-0014", "Arjun Mehta", Position.TEAM_LEAD, "Motor Claims",
                Market.SG, "Singapore HQ", "manager1", "2018-07-16"));
        employees.save(employee("lead2", "EMP-0021", "Siti Rahman", Position.TEAM_LEAD, "Property Claims",
                Market.MY, "Kuala Lumpur", "manager1", "2019-01-07"));
        employees.save(employee("officer1", "EMP-0108", "Daniel Lim", Position.CLAIMS_OFFICER, "Motor Claims",
                Market.SG, "Singapore HQ", "lead1", "2022-04-11"));
        employees.save(employee("officer2", "EMP-0077", "Mei Ling Wong", Position.SENIOR_CLAIMS_OFFICER,
                "Motor Claims", Market.HK, "Hong Kong", "lead1", "2020-09-01"));
        employees.save(employee("officer3", "EMP-0132", "Rizal Santos", Position.CLAIMS_OFFICER, "Property Claims",
                Market.PH, "Manila", "lead2", "2023-02-20"));
        log.info("Seeded {} demo employees", employees.count());
    }

    private static Employee employee(String username, String number, String name, Position position, String team,
                                     Market market, String office, String manager, String joinDate) {
        String email = name.toLowerCase().replace(" ", ".") + "@apac-claims.example";
        return new Employee(username, number, name, email, "+65 6100 " + number.substring(4), position, "Claims",
                team, market, office, manager, LocalDate.parse(joinDate), Employee.EmploymentType.FULL_TIME,
                Employee.Status.ACTIVE);
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
