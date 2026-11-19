package com.chubb.claims.claim.api;

import com.chubb.claims.claim.api.dto.ApprovalResponse;
import com.chubb.claims.claim.api.dto.ClaimDetailResponse;
import com.chubb.claims.claim.application.ClaimService;
import com.chubb.claims.claim.application.ClaimSubmission;
import com.chubb.claims.claim.domain.ClaimRuleViolationException;
import com.chubb.claims.claim.domain.ClaimStatus;
import com.chubb.claims.claim.domain.ClaimType;
import com.chubb.claims.claim.domain.InvalidClaimStateException;
import com.chubb.claims.claim.domain.Market;
import com.chubb.claims.claim.domain.Referral;
import com.chubb.claims.common.config.SecurityConfig;
import com.chubb.claims.common.security.CurrentUser;
import com.chubb.claims.common.security.Position;
import com.chubb.claims.common.web.PageResponse;
import com.chubb.claims.employee.application.EmployeeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {ClaimController.class, ApprovalController.class}, properties = "app.security.demo-users.password=test-password")
@Import(SecurityConfig.class)
class ClaimControllerTest {

    private static final String PASSWORD = "test-password";
    private static final UUID CLAIM_ID = UUID.fromString("7b1f6a3e-2c4d-4e5f-8a9b-0c1d2e3f4a5b");
    private static final String VALID_CLAIM = """
            {
              "policyNumber": "POL-SG-1001",
              "market": "SG",
              "claimType": "MOTOR",
              "incidentDate": "2026-11-01",
              "incidentDescription": "Rear-ended at a traffic light",
              "claimedAmount": 3200.00
            }
            """;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ClaimService claimService;

    @MockitoBean
    private EmployeeService employeeService;

    private static CurrentUser user(String username) {
        return argThat(user -> user != null && user.username().equals(username));
    }

    private static RequestPostProcessor as(String username) {
        return httpBasic(username, PASSWORD);
    }

    private static ClaimDetailResponse claim(ClaimStatus status) {
        return new ClaimDetailResponse(CLAIM_ID, "CLM-SG-2026-000001", "POL-SG-1001", "claimant1", Market.SG,
                ClaimType.MOTOR, LocalDate.of(2026, 11, 1), "Rear-ended at a traffic light", "SGD",
                new BigDecimal("3200.00"), status, status.waitingOn(), "Next step", Set.of(), null, null, null, null,
                List.of(), List.of(), null,
                Instant.parse("2026-11-19T10:00:00Z"), Instant.parse("2026-11-19T10:00:00Z"), true);
    }

    @Test
    void rejectsRequestsWithoutCredentialsAsProblemDetailWithoutABrowserChallenge() throws Exception {
        mvc.perform(get("/api/v1/claims/{id}", CLAIM_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("WWW-Authenticate"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Unauthorized"));
    }

    @Test
    void rejectsWrongPassword() throws Exception {
        mvc.perform(get("/api/v1/claims/{id}", CLAIM_ID).with(httpBasic("officer1", "wrong")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void forbidsClaimantsFromOfficerActions() throws Exception {
        mvc.perform(post("/api/v1/claims/{id}/assignment", CLAIM_ID).with(as("claimant1")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Forbidden"));

        verifyNoInteractions(claimService);
    }

    @Test
    void forbidsOfficersFromSubmittingClaims() throws Exception {
        mvc.perform(post("/api/v1/claims").with(as("officer1"))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_CLAIM))
                .andExpect(status().isForbidden());
    }

    @Test
    void submittingANewClaimReturnsCreatedWithLocation() throws Exception {
        given(claimService.submit(user("claimant1"), any(), eq("key-1")))
                .willReturn(new ClaimSubmission(claim(ClaimStatus.SUBMITTED), true));

        mvc.perform(post("/api/v1/claims").with(as("claimant1"))
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_CLAIM))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/v1/claims/" + CLAIM_ID)))
                .andExpect(jsonPath("$.claimNumber").value("CLM-SG-2026-000001"))
                .andExpect(jsonPath("$.status").value("SUBMITTED"));
    }

    @Test
    void replayingAnIdempotencyKeyReturnsTheOriginalClaim() throws Exception {
        given(claimService.submit(user("claimant1"), any(), eq("key-1")))
                .willReturn(new ClaimSubmission(claim(ClaimStatus.SUBMITTED), false));

        mvc.perform(post("/api/v1/claims").with(as("claimant1"))
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_CLAIM))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$.id").value(CLAIM_ID.toString()));
    }

    @Test
    void reportsEveryInvalidFieldOfASubmission() throws Exception {
        String invalid = """
                {
                  "market": "SG",
                  "claimType": "MOTOR",
                  "incidentDate": "2999-01-01",
                  "incidentDescription": "Hail damage",
                  "claimedAmount": -5
                }
                """;

        mvc.perform(post("/api/v1/claims").with(as("claimant1"))
                        .contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.policyNumber").exists())
                .andExpect(jsonPath("$.errors.incidentDate").exists())
                .andExpect(jsonPath("$.errors.claimedAmount").exists());

        verifyNoInteractions(claimService);
    }

    @Test
    void rejectsAnUnknownMarket() throws Exception {
        mvc.perform(post("/api/v1/claims").with(as("claimant1"))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_CLAIM.replace("\"SG\"", "\"XX\"")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void capsThePageSize() throws Exception {
        mvc.perform(get("/api/v1/claims").param("size", "500").with(as("officer1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.size").exists());
    }

    @Test
    void mapsAnInvalidTransitionToConflict() throws Exception {
        given(claimService.assign(eq(CLAIM_ID), user("officer1")))
                .willThrow(new InvalidClaimStateException("Cannot move claim from ASSIGNED to ASSIGNED"));

        mvc.perform(post("/api/v1/claims/{id}/assignment", CLAIM_ID).with(as("officer1")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Cannot move claim from ASSIGNED to ASSIGNED"));
    }

    @Test
    void mapsAConcurrentUpdateToConflict() throws Exception {
        given(claimService.assign(eq(CLAIM_ID), user("officer2")))
                .willThrow(new ObjectOptimisticLockingFailureException("Claim", CLAIM_ID));

        mvc.perform(post("/api/v1/claims/{id}/assignment", CLAIM_ID).with(as("officer2")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Concurrent update"));
    }

    @Test
    void mapsABusinessRuleViolationToUnprocessableContent() throws Exception {
        given(claimService.decide(eq(CLAIM_ID), user("officer1"), any(), any(), any()))
                .willThrow(new ClaimRuleViolationException("A claim must be assessed before it can be approved"));

        mvc.perform(post("/api/v1/claims/{id}/decision", CLAIM_ID).with(as("officer1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"outcome": "APPROVED", "approvedAmount": 1000.00, "reason": "Covered"}
                                """))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("A claim must be assessed before it can be approved"));
    }

    @Test
    void passesTheAuthenticatedClaimantToTheService() throws Exception {
        given(claimService.findClaimsOfClaimant(eq("claimant2"), isNull(), eq(0), eq(20))).willReturn(
                new PageResponse<>(List.of(), 0, 20, 0, 0));

        mvc.perform(get("/api/v1/claims/mine").with(as("claimant2")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void claimantsCannotListApprovals() throws Exception {
        mvc.perform(get("/api/v1/claims/approvals").with(as("claimant1")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(claimService);
    }

    @Test
    void approvalsDefaultToTheToReviewView() throws Exception {
        given(claimService.findApprovals(user("lead1"), eq(ApprovalResponse.View.TO_REVIEW), eq(0), eq(20)))
                .willReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

        mvc.perform(get("/api/v1/claims/approvals").with(as("lead1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void claimantsCannotReviewReferrals() throws Exception {
        mvc.perform(post("/api/v1/claims/{id}/approval", CLAIM_ID).with(as("claimant1"))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"outcome": "APPROVE"}
                                """))
                .andExpect(status().isForbidden());

        verifyNoInteractions(claimService);
    }

    @Test
    void managersReviewReferralsWithTheirPositionFromTheDirectory() throws Exception {
        given(employeeService.positionOf("manager1")).willReturn(Optional.of(Position.CLAIMS_MANAGER));
        given(claimService.reviewReferral(eq(CLAIM_ID),
                argThat(user -> user.position() == Position.CLAIMS_MANAGER), eq(Referral.Outcome.APPROVE), any()))
                .willReturn(claim(ClaimStatus.APPROVED));

        mvc.perform(post("/api/v1/claims/{id}/approval", CLAIM_ID).with(as("manager1"))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"outcome": "APPROVE", "comment": "Agreed"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.waitingOn").value("ASSIGNED_OFFICER"));
    }

    @Test
    void submitsWithoutAnIdempotencyKey() throws Exception {
        given(claimService.submit(user("claimant1"), any(), isNull()))
                .willReturn(new ClaimSubmission(claim(ClaimStatus.SUBMITTED), true));

        mvc.perform(post("/api/v1/claims").with(as("claimant1"))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_CLAIM))
                .andExpect(status().isCreated());
    }
}
