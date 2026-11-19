package com.chubb.insights.dashboard;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    private final DashboardQueries queries;
    private final OfficerTeams teams;

    public DashboardService(DashboardQueries queries, OfficerTeams teams) {
        this.queries = queries;
        this.teams = teams;
    }

    public ExposureReport exposure() {
        return ExposureReport.of(queries.lastEventAt(), queries.outstandingExposure());
    }

    public WorkloadReport workload() {
        return WorkloadReport.of(queries.lastEventAt(), queries.openClaimsByOfficerAndStatus(), teams);
    }

    public PerformanceReport performance() {
        return PerformanceReport.of(queries.lastEventAt(), queries.decisionsByOfficer(), teams);
    }
}
