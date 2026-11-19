package com.chubb.claims.employee.application;

import com.chubb.claims.claim.application.AuthorityLimits;
import com.chubb.claims.claim.domain.Market;
import com.chubb.claims.common.security.CurrentUser;
import com.chubb.claims.common.security.Position;
import com.chubb.claims.common.web.PageResponse;
import com.chubb.claims.employee.api.dto.EmployeeDetailResponse;
import com.chubb.claims.employee.api.dto.EmployeeDetailResponse.ApprovalAuthority;
import com.chubb.claims.employee.api.dto.EmployeeDetailResponse.CurrencyLimit;
import com.chubb.claims.employee.api.dto.EmployeeDetailResponse.Person;
import com.chubb.claims.employee.api.dto.EmployeeSummaryResponse;
import com.chubb.claims.employee.domain.Employee;
import com.chubb.claims.employee.infrastructure.EmployeeRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class EmployeeService {

    private final EmployeeRepository employees;
    private final AuthorityLimits authorityLimits;

    public EmployeeService(EmployeeRepository employees, AuthorityLimits authorityLimits) {
        this.employees = employees;
        this.authorityLimits = authorityLimits;
    }

    public Optional<Position> positionOf(String username) {
        return employees.findById(username).map(Employee::getPosition);
    }

    public Set<String> reportsOf(String username) {
        return new HashSet<>(employees.findAllReportsOf(username));
    }

    public PageResponse<EmployeeSummaryResponse> directory(CurrentUser viewer, String query, int page, int size) {
        Set<String> visible = reportsOf(viewer.username());
        visible.add(viewer.username());
        String pattern = "%" + (query == null ? "" : query.trim().toLowerCase(Locale.ROOT)) + "%";
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("fullName"));
        return PageResponse.of(employees.search(visible, pattern, pageRequest).map(EmployeeService::toSummary));
    }

    public EmployeeDetailResponse profile(CurrentUser viewer, String username) {
        boolean self = viewer.username().equals(username);
        if (!self && !reportsOf(viewer.username()).contains(username)) {
            throw new EmployeeNotFoundException(username);
        }
        return toDetail(load(username), self);
    }

    @Transactional
    public EmployeeDetailResponse updateContact(CurrentUser viewer, String phone, String officeLocation) {
        Employee employee = load(viewer.username());
        employee.updateContact(blankToNull(phone), blankToNull(officeLocation));
        return toDetail(employee, true);
    }

    private Employee load(String username) {
        return employees.findById(username).orElseThrow(() -> new EmployeeNotFoundException(username));
    }

    private EmployeeDetailResponse toDetail(Employee employee, boolean self) {
        Person manager = employee.getManagerUsername() == null
                ? null
                : employees.findById(employee.getManagerUsername()).map(EmployeeService::toPerson).orElse(null);
        List<Person> directReports = employees.findByManagerUsernameOrderByFullName(employee.getUsername()).stream()
                .map(EmployeeService::toPerson)
                .toList();
        return new EmployeeDetailResponse(
                employee.getUsername(),
                employee.getEmployeeNumber(),
                employee.getFullName(),
                employee.getEmail(),
                employee.getPhone(),
                employee.getPosition(),
                employee.getDepartment(),
                employee.getTeam(),
                employee.getMarket(),
                employee.getOfficeLocation(),
                employee.getJoinDate(),
                employee.getEmploymentType(),
                employee.getStatus(),
                manager,
                directReports,
                employees.findAllReportsOf(employee.getUsername()).size(),
                approvalAuthorityOf(employee.getPosition()),
                self);
    }

    private ApprovalAuthority approvalAuthorityOf(Position position) {
        if (position.hasUnlimitedAuthority()) {
            return new ApprovalAuthority(true, List.of());
        }
        List<CurrencyLimit> limits = Arrays.stream(Market.values())
                .map(Market::currency)
                .map(currency -> new CurrencyLimit(currency, authorityLimits.limitFor(position, currency).maximum()))
                .filter(limit -> limit.amount() != null && limit.amount().signum() > 0)
                .toList();
        return new ApprovalAuthority(false, limits);
    }

    private static EmployeeSummaryResponse toSummary(Employee employee) {
        return new EmployeeSummaryResponse(employee.getUsername(), employee.getEmployeeNumber(),
                employee.getFullName(), employee.getEmail(), employee.getPosition(), employee.getTeam(),
                employee.getMarket(), employee.getManagerUsername(), employee.getJoinDate(), employee.getStatus());
    }

    private static Person toPerson(Employee employee) {
        return new Person(employee.getUsername(), employee.getFullName(), employee.getPosition());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
