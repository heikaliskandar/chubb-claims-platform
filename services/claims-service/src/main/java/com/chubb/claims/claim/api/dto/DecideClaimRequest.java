package com.chubb.claims.claim.api.dto;

import com.chubb.claims.claim.domain.Decision;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record DecideClaimRequest(
        @NotNull Decision.Outcome outcome,
        @Positive @Digits(integer = 17, fraction = 2) BigDecimal approvedAmount,
        @NotBlank @Size(max = 1000) String reason) {
}
