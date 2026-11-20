package com.claimsplatform.claims.employee.api.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateContactRequest(
        @Size(max = 30) @Pattern(regexp = "^[+0-9 ()-]*$", message = "may contain digits, spaces, +, - and brackets only")
        String phone,
        @Size(max = 80) String officeLocation) {
}
