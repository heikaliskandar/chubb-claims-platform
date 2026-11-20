package com.claimsplatform.claims.claim.api;

import com.claimsplatform.claims.claim.api.dto.ApprovalResponse;
import com.claimsplatform.claims.claim.api.dto.ClaimDetailResponse;
import com.claimsplatform.claims.claim.api.dto.ReviewReferralRequest;
import com.claimsplatform.claims.claim.application.ClaimService;
import com.claimsplatform.claims.common.security.CurrentUser;
import com.claimsplatform.claims.common.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/claims")
@Tag(name = "Approvals")
public class ApprovalController {

    private final ClaimService claimService;

    public ApprovalController(ClaimService claimService) {
        this.claimService = claimService;
    }

    @GetMapping("/approvals")
    @PreAuthorize("hasAnyRole('OFFICER', 'MANAGER')")
    @Operation(summary = "Referrals for approval",
            description = "TO_REVIEW and DECIDED cover referrals from people below you; MY_REFERRALS are your own.")
    public PageResponse<ApprovalResponse> findApprovals(
            @RequestParam(defaultValue = "TO_REVIEW") ApprovalResponse.View view,
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            Authentication authentication) {
        return claimService.findApprovals(CurrentUser.from(authentication), view, page, size);
    }

    @PostMapping("/{claimId}/approval")
    @PreAuthorize("hasAnyRole('OFFICER', 'MANAGER')")
    @Operation(summary = "Approve, return or reject a claim referred above the recommending officer's authority",
            description = "The approver must be a different person (four-eyes) with authority for the amount.")
    public ClaimDetailResponse reviewReferral(@PathVariable UUID claimId,
                                              @Valid @RequestBody ReviewReferralRequest request,
                                              Authentication authentication) {
        return claimService.reviewReferral(claimId, CurrentUser.from(authentication), request.outcome(),
                request.comment());
    }
}
