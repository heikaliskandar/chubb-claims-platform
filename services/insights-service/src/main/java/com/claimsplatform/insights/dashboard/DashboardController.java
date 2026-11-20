package com.claimsplatform.insights.dashboard;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/insights")
@Tag(name = "Insights")
public class DashboardController {

    private final DashboardService dashboards;

    public DashboardController(DashboardService dashboards) {
        this.dashboards = dashboards;
    }

    @GetMapping("/workload")
    @PreAuthorize("hasAnyRole('OFFICER', 'MANAGER')")
    @Operation(summary = "Open claims per team and officer, by status")
    public WorkloadReport workload() {
        return dashboards.workload();
    }

    @GetMapping("/exposure")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Outstanding reserve on open claims by market, claim type and currency",
            description = "Totals are per currency; amounts in different currencies are never added together.")
    public ExposureReport exposure() {
        return dashboards.exposure();
    }

    @GetMapping("/performance")
    @PreAuthorize("hasAnyRole('OFFICER', 'MANAGER')")
    @Operation(summary = "Decisions, closures and average hours from submission to decision, per team and officer")
    public PerformanceReport performance() {
        return dashboards.performance();
    }
}
