package com.chubb.claims.employee.api.dto;

import com.chubb.claims.claim.domain.Market;
import com.chubb.claims.common.security.Position;
import com.chubb.claims.employee.domain.Employee;

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
