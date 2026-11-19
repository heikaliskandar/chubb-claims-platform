package com.chubb.claims.demo;

import com.chubb.claims.claim.application.ClaimService;
import com.chubb.claims.claim.domain.ClaimDetails;
import com.chubb.claims.claim.domain.ClaimType;
import com.chubb.claims.claim.domain.Decision;
import com.chubb.claims.claim.domain.Market;
import com.chubb.claims.claim.domain.Referral;
import com.chubb.claims.claim.infrastructure.ClaimRepository;
import com.chubb.claims.common.security.CurrentUser;
import com.chubb.claims.common.security.Position;
import com.chubb.claims.common.security.Role;
import com.chubb.claims.document.application.DocumentService;
import com.chubb.claims.document.domain.DocumentCategory;
import com.chubb.claims.employee.domain.Employee;
import com.chubb.claims.employee.infrastructure.EmployeeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One claim at each stage of the lifecycle, so every screen and every role has something to act on.
 * The scenarios are listed in docs/user-flows.md; keep the two in step.
 */
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
    private static final CurrentUser LEAD_1 = new CurrentUser("lead1", Role.OFFICER, Position.TEAM_LEAD);

    private final ClaimService claimService;
    private final ClaimRepository claims;
    private final EmployeeRepository employees;
    private final DocumentService documents;
    private final boolean claimsEnabled;

    public DemoDataSeeder(ClaimService claimService, ClaimRepository claims, EmployeeRepository employees,
                          DocumentService documents,
                          @Value("${app.demo-data.claims-enabled:true}") boolean claimsEnabled) {
        this.claimService = claimService;
        this.claims = claims;
        this.employees = employees;
        this.documents = documents;
        this.claimsEnabled = claimsEnabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (employees.count() == 0) {
            seedEmployees();
        }
        if (claimsEnabled && claims.count() == 0) {
            seedClaims();
        }
    }

    private void seedClaims() {
        submit(CLAIMANT_1, Market.SG, ClaimType.MOTOR, "POL-SG-M-204518", 2, "3200.00",
                "Side mirror and driver door scraped by a reversing van in a Tampines car park.");

        submit(CLAIMANT_2, Market.MY, ClaimType.PROPERTY, "POL-MY-P-118207", 1, "18500.00",
                "Thunderstorm tore off part of the roof; rain damaged the ceiling and living room furniture.");

        UUID assigned = submit(CLAIMANT_1, Market.PH, ClaimType.PROPERTY, "POL-PH-P-330914", 4, "95000.00",
                "Electrical fault started a small fire in the kitchen of a Makati condominium unit.");
        claimService.assign(assigned, OFFICER_3);

        UUID readyToDecide = submit(CLAIMANT_1, Market.SG, ClaimType.MOTOR, "POL-SG-M-207731", 6, "6800.00",
                "Rear-ended while stopped at a junction on Bukit Timah Road; bumper and tail lights damaged.");
        startReview(readyToDecide, OFFICER_1);
        UUID question = claimService.requestInformation(readyToDecide, OFFICER_1,
                "Please share the workshop quotation and the other driver's insurer.").informationRequests().get(0).id();
        attach(readyToDecide, CLAIMANT_1, DocumentCategory.REPAIR_QUOTE, "repair-quote.pdf");
        attach(readyToDecide, CLAIMANT_1, DocumentCategory.PHOTO, "rear-bumper-damage.jpg");
        claimService.provideInformation(readyToDecide, question, CLAIMANT_1,
                "Quotation WS-88213 from Ah Seng Motor Works attached; the other driver is insured with ABC General.");
        claimService.assess(readyToDecide, OFFICER_1, new BigDecimal("6500.00"),
                "Quotation verified against parts price list; labour hours reasonable.");

        UUID waitingOnClaimant = submit(CLAIMANT_2, Market.TH, ClaimType.MOTOR, "POL-TH-M-552190", 5, "52000.00",
                "Collision with a motorcycle on the Bang Na expressway; front bumper and headlamp damaged.");
        startReview(waitingOnClaimant, OFFICER_1);
        claimService.requestInformation(waitingOnClaimant, OFFICER_1,
                "Please provide the police report number and photos of both vehicles.");

        UUID referred = submit(CLAIMANT_2, Market.SG, ClaimType.PROPERTY, "POL-SG-P-401276", 9, "85000.00",
                "Fire spread from the neighbouring unit; kitchen and two bedrooms smoke- and water-damaged.");
        startReview(referred, OFFICER_1);
        attach(referred, OFFICER_1, DocumentCategory.ADJUSTER_REPORT, "loss-adjuster-report.pdf");
        claimService.assess(referred, OFFICER_1, new BigDecimal("80000.00"), "Loss adjuster report LA-5521 received.");
        claimService.decide(referred, OFFICER_1, Decision.Outcome.APPROVED, new BigDecimal("78000.00"),
                "Covered; loss adjuster report supports the amount after depreciation.");

        UUID approvedAfterReferral = submit(CLAIMANT_1, Market.HK, ClaimType.MOTOR, "POL-HK-M-660342", 12,
                "420000.00", "Vehicle written off after a multi-car collision on the Tuen Mun Road.");
        startReview(approvedAfterReferral, OFFICER_2);
        claimService.assess(approvedAfterReferral, OFFICER_2, new BigDecimal("400000.00"),
                "Total loss; market value from two dealer valuations.");
        claimService.decide(approvedAfterReferral, OFFICER_2, Decision.Outcome.APPROVED, new BigDecimal("390000.00"),
                "Total loss at agreed market value less excess.");
        claimService.reviewReferral(approvedAfterReferral, LEAD_1, Referral.Outcome.APPROVE,
                "Valuations are consistent; approved.");

        UUID rejected = submit(CLAIMANT_2, Market.PH, ClaimType.PROPERTY, "POL-PH-P-327755", 15, "120000.00",
                "Water stains and mould on the bedroom ceiling from a long-standing roof leak.");
        startReview(rejected, OFFICER_3);
        claimService.assess(rejected, OFFICER_3, BigDecimal.ZERO, "Surveyor confirms damage built up over months.");
        claimService.decide(rejected, OFFICER_3, Decision.Outcome.REJECTED, null,
                "Gradual deterioration and lack of maintenance are excluded under section 4.2 of the policy.");

        UUID settled = submit(CLAIMANT_1, Market.ID, ClaimType.MOTOR, "POL-ID-M-781120", 20, "7500000.00",
                "Rear-ended at a traffic light in South Jakarta; boot lid and rear bumper replaced.");
        startReview(settled, OFFICER_2);
        claimService.assess(settled, OFFICER_2, new BigDecimal("7000000.00"), "Authorised workshop invoice.");
        claimService.decide(settled, OFFICER_2, Decision.Outcome.APPROVED, new BigDecimal("6800000.00"),
                "Covered under comprehensive policy, less excess of IDR 200,000.");
        claimService.settle(settled, OFFICER_2);

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

    private UUID submit(CurrentUser claimant, Market market, ClaimType type, String policyNumber, int daysAgo,
                        String amount, String description) {
        ClaimDetails details = new ClaimDetails(policyNumber, market, type, LocalDate.now().minusDays(daysAgo),
                description, new BigDecimal(amount));
        return claimService.submit(claimant, details, null).claim().id();
    }

    private void attach(UUID claimId, CurrentUser uploader, DocumentCategory category, String fileName) {
        try {
            byte[] content = new ClassPathResource("demo/" + fileName).getContentAsByteArray();
            documents.upload(claimId, uploader, category, fileName, content);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private void startReview(UUID claimId, CurrentUser officer) {
        claimService.assign(claimId, officer);
        claimService.startReview(claimId, officer);
    }
}
