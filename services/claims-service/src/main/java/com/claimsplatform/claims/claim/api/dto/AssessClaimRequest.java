package com.claimsplatform.claims.claim.api.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record AssessClaimRequest(
        @NotNull @PositiveOrZero @Digits(integer = 17, fraction = 2) BigDecimal assessedAmount,
        @Size(max = 2000) String notes) {
}
