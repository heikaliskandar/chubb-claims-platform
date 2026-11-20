package com.claimsplatform.claims.claim.application;

import com.claimsplatform.claims.claim.api.dto.ClaimDetailResponse;

public record ClaimSubmission(ClaimDetailResponse claim, boolean created) {
}
