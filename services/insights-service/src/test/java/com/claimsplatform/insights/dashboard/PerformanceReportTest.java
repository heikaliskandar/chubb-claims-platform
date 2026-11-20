package com.claimsplatform.insights.dashboard;

import com.claimsplatform.insights.dashboard.PerformanceReport.OfficerPerformance;
import com.claimsplatform.insights.dashboard.PerformanceReport.Row;
import com.claimsplatform.insights.dashboard.PerformanceReport.TeamPerformance;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PerformanceReportTest {

    private static final long HOUR = 3600;
    private static final OfficerTeams TEAMS = new OfficerTeams(Map.of(
            "officer1", "Motor Claims",
            "officer2", "Motor Claims"));

    @Test
    void teamAverageIsWeightedByNumberOfDecisions() {
        PerformanceReport report = PerformanceReport.of(null, List.of(
                new Row("officer1", 3, 0, 1, 3 * 10 * HOUR),
                new Row("officer2", 0, 1, 1, 50 * HOUR)), TEAMS);

        TeamPerformance team = report.teams().getFirst();
        assertThat(team.decided()).isEqualTo(4);
        assertThat(team.approved()).isEqualTo(3);
        assertThat(team.rejected()).isEqualTo(1);
        assertThat(team.closed()).isEqualTo(2);
        assertThat(team.averageHoursToDecision()).isEqualByComparingTo("20.0");
        assertThat(team.officers()).extracting(OfficerPerformance::averageHoursToDecision)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("10.0"), new BigDecimal("50.0"));
    }

    @Test
    void officersWithoutDecisionsHaveNoAverage() {
        PerformanceReport report = PerformanceReport.of(null, List.of(), TEAMS);

        TeamPerformance team = report.teams().getFirst();
        assertThat(team.decided()).isZero();
        assertThat(team.averageHoursToDecision()).isNull();
        assertThat(team.officers()).hasSize(2);
    }
}
