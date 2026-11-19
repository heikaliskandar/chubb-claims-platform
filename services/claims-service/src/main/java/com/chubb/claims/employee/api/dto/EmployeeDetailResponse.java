package com.chubb.claims.employee.api.dto;

import com.chubb.claims.claim.domain.Market;
import com.chubb.claims.common.security.Position;
import com.chubb.claims.employee.domain.Employee;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record EmployeeDetailResponse(
        String username,
        String employeeNumber,
        String fullName,
        String email,
        String phone,
        Position position,
        String department,
        String team,
        Market market,
        String officeLocation,
        LocalDate joinDate,
        Employee.EmploymentType employmentType,
        Employee.Status status,
        Person manager,
        List<Person> directReports,
        int totalReports,
        ApprovalAuthority approvalAuthority,
        boolean self) {

    public record Person(String username, String fullName, Position position) {
    }

    public record ApprovalAuthority(boolean unlimited, List<CurrencyLimit> limits) {
    }

    public record CurrencyLimit(String currency, BigDecimal amount) {
    }
}
