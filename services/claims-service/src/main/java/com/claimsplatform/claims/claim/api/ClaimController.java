package com.claimsplatform.claims.claim.api;

import com.claimsplatform.claims.claim.api.dto.AssessClaimRequest;
import com.claimsplatform.claims.claim.api.dto.ClaimDetailResponse;
import com.claimsplatform.claims.claim.api.dto.ClaimSummaryResponse;
import com.claimsplatform.claims.claim.api.dto.DecideClaimRequest;
import com.claimsplatform.claims.claim.api.dto.ProvideInformationRequest;
import com.claimsplatform.claims.claim.api.dto.RequestInformationRequest;
import com.claimsplatform.claims.claim.api.dto.SubmitClaimRequest;
import com.claimsplatform.claims.claim.api.dto.UpdateClaimRequest;
import com.claimsplatform.claims.claim.api.dto.WithdrawClaimRequest;
import com.claimsplatform.claims.claim.application.ClaimService;
import com.claimsplatform.claims.claim.application.ClaimSubmission;
import com.claimsplatform.claims.claim.domain.ClaimStatus;
import com.claimsplatform.claims.common.security.CurrentUser;
import com.claimsplatform.claims.common.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/claims")
@Tag(name = "Claims")
public class ClaimController {

    private final ClaimService claimService;

    public ClaimController(ClaimService claimService) {
        this.claimService = claimService;
    }

    @PostMapping
    @PreAuthorize("hasRole('CLAIMANT')")
    @Operation(summary = "Submit a claim",
            description = "Repeating a request with the same Idempotency-Key returns the original claim with 200.")
    @ApiResponse(responseCode = "201", description = "Created; Location points to the new claim")
    @ApiResponse(responseCode = "200", description = "Same Idempotency-Key as an earlier submission; the original claim")
    public ResponseEntity<ClaimDetailResponse> submit(
            @Valid @RequestBody SubmitClaimRequest request,
            @RequestHeader(name = "Idempotency-Key", required = false) @Size(max = 100) String idempotencyKey,
            Authentication authentication) {
        ClaimSubmission submission =
                claimService.submit(CurrentUser.from(authentication), request.toClaimDetails(), idempotencyKey);
        if (!submission.created()) {
            return ResponseEntity.ok(submission.claim());
        }
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(submission.claim().id())
                .toUri();
        return ResponseEntity.created(location).body(submission.claim());
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OFFICER', 'MANAGER')")
    @Operation(summary = "Claims queue, oldest first, optionally filtered by status and assigned officer",
            description = "Shows unassigned claims plus claims assigned to the user or anyone in their reporting line.")
    public PageResponse<ClaimSummaryResponse> findClaims(
            @RequestParam(required = false) ClaimStatus status,
            @RequestParam(required = false) String assignedTo,
            @RequestParam(required = false) Boolean closed,
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            Authentication authentication) {
        return claimService.findClaims(CurrentUser.from(authentication), status, assignedTo, closed, page, size);
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('CLAIMANT')")
    @Operation(summary = "The authenticated claimant's claims, most recently updated first",
            description = "closed=false for active claims, closed=true for settled or rejected ones.")
    public PageResponse<ClaimSummaryResponse> findMyClaims(
            @RequestParam(required = false) Boolean closed,
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            Authentication authentication) {
        return claimService.findClaimsOfClaimant(authentication.getName(), closed, page, size);
    }

    @GetMapping("/{claimId}")
    @Operation(summary = "Claim detail", description = "Claimants can only see their own claims.")
    public ClaimDetailResponse getClaim(@PathVariable UUID claimId, Authentication authentication) {
        return claimService.getClaim(claimId, CurrentUser.from(authentication));
    }

    @PutMapping("/{claimId}")
    @PreAuthorize("hasRole('CLAIMANT')")
    @Operation(summary = "Edit a claim before it is picked up",
            description = "Only the claimant, and only while the claim is SUBMITTED (no officer has picked it up). "
                    + "The market cannot change because it sets the claim number and currency. The change is "
                    + "recorded in the activity history and published as CLAIM_DETAILS_UPDATED.")
    @ApiResponse(responseCode = "200", description = "Updated")
    @ApiResponse(responseCode = "409", description = "An officer has already picked up the claim")
    @ApiResponse(responseCode = "422", description = "The market was changed")
    public ClaimDetailResponse updateDetails(@PathVariable UUID claimId, @Valid @RequestBody UpdateClaimRequest request,
                                             Authentication authentication) {
        return claimService.updateDetails(claimId, CurrentUser.from(authentication), request.toClaimDetails());
    }

    @PostMapping("/{claimId}/withdrawal")
    @PreAuthorize("hasRole('CLAIMANT')")
    @Operation(summary = "Withdraw a claim before it is picked up",
            description = "Only the claimant, and only while the claim is SUBMITTED. Moves it to WITHDRAWN, a final "
                    + "status.")
    @ApiResponse(responseCode = "200", description = "Withdrawn")
    @ApiResponse(responseCode = "409", description = "An officer has already picked up the claim")
    public ClaimDetailResponse withdraw(@PathVariable UUID claimId, @Valid @RequestBody WithdrawClaimRequest request,
                                        Authentication authentication) {
        return claimService.withdraw(claimId, CurrentUser.from(authentication), request.reason());
    }

    @PostMapping("/{claimId}/assignment")
    @PreAuthorize("hasRole('OFFICER')")
    @Operation(summary = "Pick up an unassigned claim")
    public ClaimDetailResponse assign(@PathVariable UUID claimId, Authentication authentication) {
        return claimService.assign(claimId, CurrentUser.from(authentication));
    }

    @PostMapping("/{claimId}/review")
    @PreAuthorize("hasRole('OFFICER')")
    @Operation(summary = "Start reviewing an assigned claim")
    public ClaimDetailResponse startReview(@PathVariable UUID claimId, Authentication authentication) {
        return claimService.startReview(claimId, CurrentUser.from(authentication));
    }

    @PostMapping("/{claimId}/information-requests")
    @PreAuthorize("hasRole('OFFICER')")
    @Operation(summary = "Ask the claimant for more information")
    public ClaimDetailResponse requestInformation(@PathVariable UUID claimId,
                                                  @Valid @RequestBody RequestInformationRequest request,
                                                  Authentication authentication) {
        return claimService.requestInformation(claimId, CurrentUser.from(authentication), request.question());
    }

    @PostMapping("/{claimId}/information-requests/{requestId}/response")
    @PreAuthorize("hasRole('CLAIMANT')")
    @Operation(summary = "Answer an information request")
    public ClaimDetailResponse provideInformation(@PathVariable UUID claimId,
                                                  @PathVariable UUID requestId,
                                                  @Valid @RequestBody ProvideInformationRequest request,
                                                  Authentication authentication) {
        return claimService.provideInformation(claimId, requestId, CurrentUser.from(authentication),
                request.response());
    }

    @PostMapping("/{claimId}/assessment")
    @PreAuthorize("hasRole('OFFICER')")
    @Operation(summary = "Assess the claim and set its reserve")
    public ClaimDetailResponse assess(@PathVariable UUID claimId,
                                      @Valid @RequestBody AssessClaimRequest request,
                                      Authentication authentication) {
        return claimService.assess(claimId, CurrentUser.from(authentication), request.assessedAmount(),
                request.notes());
    }

    @PostMapping("/{claimId}/decision")
    @PreAuthorize("hasRole('OFFICER')")
    @Operation(summary = "Approve or reject the claim",
            description = "Approvals above the officer's authority limit are referred (PENDING_APPROVAL).")
    public ClaimDetailResponse decide(@PathVariable UUID claimId,
                                      @Valid @RequestBody DecideClaimRequest request,
                                      Authentication authentication) {
        return claimService.decide(claimId, CurrentUser.from(authentication), request.outcome(),
                request.approvedAmount(), request.reason());
    }

    @PostMapping("/{claimId}/settlement")
    @PreAuthorize("hasRole('OFFICER')")
    @Operation(summary = "Mark an approved claim as paid")
    public ClaimDetailResponse settle(@PathVariable UUID claimId, Authentication authentication) {
        return claimService.settle(claimId, CurrentUser.from(authentication));
    }
}
