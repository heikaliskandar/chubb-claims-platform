package com.claimsplatform.claims.employee.api.dto;

import com.claimsplatform.claims.claim.domain.Market;
import com.claimsplatform.claims.common.security.Position;
import com.claimsplatform.claims.employee.domain.Employee;

import java.time.LocalDate;

public record EmployeeSummaryResponse(
        String username,
        String employeeNumber,
        String fullName,
        String email,
        Position position,
        String team,
        Market market,
        String managerUsername,
        LocalDate joinDate,
        Employee.Status status) {
}
