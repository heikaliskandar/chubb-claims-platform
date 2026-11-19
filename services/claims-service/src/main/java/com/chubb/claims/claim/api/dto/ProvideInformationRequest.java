package com.chubb.claims.claim.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProvideInformationRequest(@NotBlank @Size(max = 4000) String response) {
}
