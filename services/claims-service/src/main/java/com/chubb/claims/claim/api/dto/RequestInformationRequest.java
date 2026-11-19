package com.chubb.claims.claim.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RequestInformationRequest(@NotBlank @Size(max = 2000) String question) {
}
