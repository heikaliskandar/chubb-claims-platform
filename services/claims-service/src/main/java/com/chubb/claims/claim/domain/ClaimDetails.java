package com.chubb.claims.claim.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ClaimDetails(
        String policyNumber,
        Market market,
        ClaimType claimType,
        LocalDate incidentDate,
        String incidentDescription,
        BigDecimal claimedAmount) {
}
