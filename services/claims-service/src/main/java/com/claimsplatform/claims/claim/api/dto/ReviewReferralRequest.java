package com.claimsplatform.claims.claim.api.dto;

import com.claimsplatform.claims.claim.domain.Referral;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReviewReferralRequest(@NotNull Referral.Outcome outcome, @Size(max = 1000) String comment) {
}
