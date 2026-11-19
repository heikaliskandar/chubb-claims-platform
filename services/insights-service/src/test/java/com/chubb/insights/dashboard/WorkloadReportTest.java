package com.chubb.insights.dashboard;

import com.chubb.insights.dashboard.WorkloadReport.OfficerWorkload;
import com.chubb.insights.dashboard.WorkloadReport.Row;
import com.chubb.insights.dashboard.WorkloadReport.TeamWorkload;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class WorkloadReportTest {

    private static final OfficerTeams TEAMS = new OfficerTeams(Map.of(
            "officer1", "Motor Claims",
            "officer2", "Motor Claims",
            "officer3", "Property Claims"));

    @Test
    void groupsOpenClaimsByTeamOfficerAndStatus() {
        WorkloadReport report = WorkloadReport.of(null, List.of(
                new Row(null, "SUBMITTED", 4),
                new Row("officer1", "ASSIGNED", 2),
                new Row("officer1", "UNDER_REVIEW", 3),
                new Row("officer3", "INFO_REQUESTED", 1)), TEAMS);

        assertThat(report.unassignedClaims()).isEqualTo(4);
        assertThat(report.teams()).extracting(TeamWorkload::team, TeamWorkload::openClaims)
                .containsExactly(
                        tuple("Motor Claims", 5L),
                        tuple("Property Claims", 1L));
        assertThat(report.teams().getFirst().officers().getFirst())
                .isEqualTo(new OfficerWorkload("officer1", 5, Map.of("ASSIGNED", 2L, "UNDER_REVIEW", 3L)));
    }

    @Test
    void showsConfiguredOfficersWithNoOpenClaims() {
        WorkloadReport report = WorkloadReport.of(null, List.of(), TEAMS);

        assertThat(report.teams().getFirst().officers())
                .extracting(OfficerWorkload::officerId, OfficerWorkload::openClaims)
                .containsExactly(
                        tuple("officer1", 0L),
                        tuple("officer2", 0L));
    }

    @Test
    void putsOfficersWithoutATeamInTheUnmappedTeam() {
        WorkloadReport report = WorkloadReport.of(null, List.of(new Row("officer9", "ASSIGNED", 1)), TEAMS);

        assertThat(report.teams()).extracting(TeamWorkload::team).contains(OfficerTeams.UNMAPPED_TEAM);
    }
}
