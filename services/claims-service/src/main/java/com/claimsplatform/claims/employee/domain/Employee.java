package com.claimsplatform.claims.employee.domain;

import com.claimsplatform.claims.claim.domain.Market;
import com.claimsplatform.claims.common.security.Position;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(name = "employee")
public class Employee {

    public enum EmploymentType { FULL_TIME, PART_TIME, CONTRACT }

    public enum Status { ACTIVE, ON_LEAVE, INACTIVE }

    @Id
    private String username;

    private String employeeNumber;
    private String fullName;
    private String email;
    private String phone;

    @Enumerated(EnumType.STRING)
    private Position position;

    private String department;
    private String team;

    @Enumerated(EnumType.STRING)
    private Market market;

    private String officeLocation;
    private String managerUsername;
    private LocalDate joinDate;

    @Enumerated(EnumType.STRING)
    private EmploymentType employmentType;

    @Enumerated(EnumType.STRING)
    private Status status;

    protected Employee() {
    }

    public Employee(String username, String employeeNumber, String fullName, String email, String phone,
                    Position position, String department, String team, Market market, String officeLocation,
                    String managerUsername, LocalDate joinDate, EmploymentType employmentType, Status status) {
        this.username = username;
        this.employeeNumber = employeeNumber;
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.position = position;
        this.department = department;
        this.team = team;
        this.market = market;
        this.officeLocation = officeLocation;
        this.managerUsername = managerUsername;
        this.joinDate = joinDate;
        this.employmentType = employmentType;
        this.status = status;
    }

    public void updateContact(String phone, String officeLocation) {
        this.phone = phone;
        this.officeLocation = officeLocation;
    }

    public String getUsername() {
        return username;
    }

    public String getEmployeeNumber() {
        return employeeNumber;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public Position getPosition() {
        return position;
    }

    public String getDepartment() {
        return department;
    }

    public String getTeam() {
        return team;
    }

    public Market getMarket() {
        return market;
    }

    public String getOfficeLocation() {
        return officeLocation;
    }

    public String getManagerUsername() {
        return managerUsername;
    }

    public LocalDate getJoinDate() {
        return joinDate;
    }

    public EmploymentType getEmploymentType() {
        return employmentType;
    }

    public Status getStatus() {
        return status;
    }
}
