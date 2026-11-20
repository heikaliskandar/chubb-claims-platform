package com.claimsplatform.claims.employee.application;

import com.claimsplatform.claims.claim.application.AuthorityLimits;
import com.claimsplatform.claims.claim.domain.Market;
import com.claimsplatform.claims.common.security.CurrentUser;
import com.claimsplatform.claims.common.security.Position;
import com.claimsplatform.claims.common.security.Role;
import com.claimsplatform.claims.employee.api.dto.EmployeeDetailResponse;
import com.claimsplatform.claims.employee.domain.Employee;
import com.claimsplatform.claims.employee.infrastructure.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class EmployeeServiceTest {

    private static final CurrentUser LEAD = new CurrentUser("lead1", Role.OFFICER, Position.TEAM_LEAD);
    private static final CurrentUser OFFICER = new CurrentUser("officer1", Role.OFFICER, Position.CLAIMS_OFFICER);

    private final EmployeeRepository repository = mock(EmployeeRepository.class);
    private final AuthorityLimits limits = new AuthorityLimits(Map.of(
            Position.CLAIMS_OFFICER, Map.of("SGD", new BigDecimal("10000")),
            Position.TEAM_LEAD, Map.of("SGD", new BigDecimal("250000"))));
    private final EmployeeService service = new EmployeeService(repository, limits);

    private static Employee employee(String username, Position position, String manager) {
        return new Employee(username, "EMP-" + username, username, username + "@example.com", null, position,
                "Claims", "Motor Claims", Market.SG, "Singapore HQ", manager, LocalDate.of(2020, 1, 1),
                Employee.EmploymentType.FULL_TIME, Employee.Status.ACTIVE);
    }

    @BeforeEach
    void organisation() {
        given(repository.findById("manager1"))
                .willReturn(Optional.of(employee("manager1", Position.CLAIMS_MANAGER, null)));
        given(repository.findById("lead1")).willReturn(Optional.of(employee("lead1", Position.TEAM_LEAD, "manager1")));
        given(repository.findById("officer1"))
                .willReturn(Optional.of(employee("officer1", Position.CLAIMS_OFFICER, "lead1")));
        given(repository.findAllReportsOf("lead1")).willReturn(List.of("officer1"));
        given(repository.findAllReportsOf("officer1")).willReturn(List.of());
        given(repository.findByManagerUsernameOrderByFullName("lead1"))
                .willReturn(List.of(employee("officer1", Position.CLAIMS_OFFICER, "lead1")));
    }

    @Test
    void aManagerCanOpenTheProfileOfSomeoneBelowThem() {
        EmployeeDetailResponse profile = service.profile(LEAD, "officer1");

        assertThat(profile.username()).isEqualTo("officer1");
        assertThat(profile.self()).isFalse();
        assertThat(profile.manager().username()).isEqualTo("lead1");
    }

    @Test
    void nobodyCanOpenTheProfileOfSomeoneAboveThem() {
        assertThatThrownBy(() -> service.profile(OFFICER, "lead1")).isInstanceOf(EmployeeNotFoundException.class);
        assertThatThrownBy(() -> service.profile(LEAD, "manager1")).isInstanceOf(EmployeeNotFoundException.class);
    }

    @Test
    void ownProfileShowsTheDirectManagerByNameOnly() {
        EmployeeDetailResponse profile = service.profile(OFFICER, "officer1");

        assertThat(profile.self()).isTrue();
        assertThat(profile.manager())
                .isEqualTo(new EmployeeDetailResponse.Person("lead1", "lead1", Position.TEAM_LEAD));
    }

    @Test
    void profileListsDirectReportsAndApprovalAuthorityPerCurrency() {
        EmployeeDetailResponse profile = service.profile(LEAD, "lead1");

        assertThat(profile.directReports()).extracting(EmployeeDetailResponse.Person::username)
                .containsExactly("officer1");
        assertThat(profile.totalReports()).isEqualTo(1);
        assertThat(profile.approvalAuthority().unlimited()).isFalse();
        assertThat(profile.approvalAuthority().limits())
                .containsExactly(new EmployeeDetailResponse.CurrencyLimit("SGD", new BigDecimal("250000")));
    }

    @Test
    void theClaimsManagerHasUnlimitedAuthority() {
        given(repository.findAllReportsOf("manager1")).willReturn(List.of("lead1", "officer1"));

        EmployeeDetailResponse profile = service.profile(
                new CurrentUser("manager1", Role.MANAGER, Position.CLAIMS_MANAGER), "manager1");

        assertThat(profile.approvalAuthority().unlimited()).isTrue();
        assertThat(profile.totalReports()).isEqualTo(2);
    }
}
