package com.chubb.claims.employee.infrastructure;

import com.chubb.claims.TestcontainersConfiguration;
import com.chubb.claims.claim.domain.Market;
import com.chubb.claims.common.security.Position;
import com.chubb.claims.employee.domain.Employee;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "app.security.demo-users.password=test-password")
@Import(TestcontainersConfiguration.class)
class EmployeeRepositoryIT {

    @Autowired
    private EmployeeRepository employees;

    private Employee employee(String username, Position position, String manager) {
        return new Employee(username, "IT-" + username, username, username + "@it.example", null, position,
                "Claims", "Motor Claims", Market.SG, null, manager, LocalDate.of(2021, 5, 1),
                Employee.EmploymentType.FULL_TIME, Employee.Status.ACTIVE);
    }

    @Test
    void findsEveryoneBelowAPersonButNobodyAboveOrBeside() {
        employees.save(employee("it-head", Position.CLAIMS_MANAGER, null));
        employees.save(employee("it-lead-a", Position.TEAM_LEAD, "it-head"));
        employees.save(employee("it-lead-b", Position.TEAM_LEAD, "it-head"));
        employees.save(employee("it-officer-a1", Position.CLAIMS_OFFICER, "it-lead-a"));
        employees.save(employee("it-officer-b1", Position.CLAIMS_OFFICER, "it-lead-b"));

        assertThat(employees.findAllReportsOf("it-head"))
                .containsExactlyInAnyOrder("it-lead-a", "it-lead-b", "it-officer-a1", "it-officer-b1");
        assertThat(employees.findAllReportsOf("it-lead-a")).containsExactly("it-officer-a1");
        assertThat(employees.findAllReportsOf("it-officer-a1")).isEmpty();
    }
}
