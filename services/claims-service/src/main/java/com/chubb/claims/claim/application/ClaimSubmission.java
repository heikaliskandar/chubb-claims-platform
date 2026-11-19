package com.chubb.claims.claim.application;

import com.chubb.claims.claim.api.dto.ClaimDetailResponse;

public record ClaimSubmission(ClaimDetailResponse claim, boolean created) {
}
