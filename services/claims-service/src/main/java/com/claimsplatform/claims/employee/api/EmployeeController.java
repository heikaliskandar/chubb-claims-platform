package com.claimsplatform.claims.employee.api;

import com.claimsplatform.claims.common.security.CurrentUser;
import com.claimsplatform.claims.common.web.PageResponse;
import com.claimsplatform.claims.employee.api.dto.EmployeeDetailResponse;
import com.claimsplatform.claims.employee.api.dto.EmployeeSummaryResponse;
import com.claimsplatform.claims.employee.api.dto.UpdateContactRequest;
import com.claimsplatform.claims.employee.application.EmployeeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/employees")
@PreAuthorize("hasAnyRole('OFFICER', 'MANAGER')")
@Tag(name = "Employees")
public class EmployeeController {

    private final EmployeeService employeeService;

    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @GetMapping
    @Operation(summary = "Directory of the current user and everyone who reports to them, directly or indirectly")
    public PageResponse<EmployeeSummaryResponse> directory(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            Authentication authentication) {
        return employeeService.directory(CurrentUser.from(authentication), q, page, size);
    }

    @GetMapping("/me")
    @Operation(summary = "The current user's own profile, including the name of their direct manager")
    public EmployeeDetailResponse me(Authentication authentication) {
        CurrentUser user = CurrentUser.from(authentication);
        return employeeService.profile(user, user.username());
    }

    @PatchMapping("/me")
    @Operation(summary = "Update the current user's own contact details")
    public EmployeeDetailResponse updateMyContact(@Valid @RequestBody UpdateContactRequest request,
                                                  Authentication authentication) {
        return employeeService.updateContact(CurrentUser.from(authentication), request.phone(),
                request.officeLocation());
    }

    @GetMapping("/{username}")
    @Operation(summary = "Profile of someone in the current user's reporting line",
            description = "People above or beside the current user return 404.")
    public EmployeeDetailResponse profile(@PathVariable String username, Authentication authentication) {
        return employeeService.profile(CurrentUser.from(authentication), username);
    }
}
