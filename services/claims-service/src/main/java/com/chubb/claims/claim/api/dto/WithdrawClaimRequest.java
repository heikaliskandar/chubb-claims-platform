package com.chubb.claims.claim.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

public record WithdrawClaimRequest(
        @Schema(description = "Why the claim is being withdrawn. Optional.", nullable = true)
        @Size(max = 1000) String reason) {
}
