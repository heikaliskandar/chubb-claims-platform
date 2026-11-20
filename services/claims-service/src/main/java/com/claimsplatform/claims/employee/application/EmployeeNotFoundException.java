package com.claimsplatform.claims.employee.application;

public class EmployeeNotFoundException extends RuntimeException {

    public EmployeeNotFoundException(String username) {
        super("Employee " + username + " not found");
    }
}
