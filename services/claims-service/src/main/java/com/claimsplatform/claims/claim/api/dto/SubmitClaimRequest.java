package com.claimsplatform.claims.claim.api.dto;

import com.claimsplatform.claims.claim.domain.ClaimDetails;
import com.claimsplatform.claims.claim.domain.ClaimType;
import com.claimsplatform.claims.claim.domain.Market;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SubmitClaimRequest(
        @NotBlank @Size(max = 50) String policyNumber,
        @NotNull Market market,
        @NotNull ClaimType claimType,
        @NotNull @PastOrPresent LocalDate incidentDate,
        @NotBlank @Size(max = 2000) String incidentDescription,
        @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal claimedAmount) {

    public ClaimDetails toClaimDetails() {
        return new ClaimDetails(policyNumber, market, claimType, incidentDate, incidentDescription, claimedAmount);
    }
}
