package com.claimsplatform.claims.employee.infrastructure;

import com.claimsplatform.claims.employee.domain.Employee;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface EmployeeRepository extends JpaRepository<Employee, String> {

    @Query(value = """
            WITH RECURSIVE reports AS (
                SELECT username FROM employee WHERE manager_username = :username
                -- UNION (not UNION ALL) de-duplicates, so a cycle in the reporting line cannot recurse forever.
                UNION
                SELECT e.username FROM employee e JOIN reports r ON e.manager_username = r.username
            )
            SELECT username FROM reports
            """, nativeQuery = true)
    List<String> findAllReportsOf(String username);

    List<Employee> findByManagerUsernameOrderByFullName(String managerUsername);

    @Query("""
            SELECT e FROM Employee e
            WHERE e.username IN :usernames
              AND (LOWER(e.fullName) LIKE :pattern
                   OR LOWER(e.email) LIKE :pattern
                   OR LOWER(e.team) LIKE :pattern
                   OR LOWER(e.employeeNumber) LIKE :pattern)
            """)
    Page<Employee> search(Collection<String> usernames, String pattern, Pageable pageable);
}
