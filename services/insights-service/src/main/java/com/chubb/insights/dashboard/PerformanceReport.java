package com.chubb.insights.dashboard;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public record PerformanceReport(Instant asOf, List<TeamPerformance> teams) {

    private static final long SECONDS_PER_HOUR = 3600;

    public record TeamPerformance(String team, long decided, long approved, long rejected, long closed,
                                  BigDecimal averageHoursToDecision, List<OfficerPerformance> officers) {
    }

    public record OfficerPerformance(String officerId, long decided, long approved, long rejected, long closed,
                                     BigDecimal averageHoursToDecision) {
    }

    public record Row(String officerId, long approved, long rejected, long closed, long secondsToDecision) {

        static Row empty(String officerId) {
            return new Row(officerId, 0, 0, 0, 0);
        }

        long decided() {
            return approved + rejected;
        }

        Row plus(Row other) {
            return new Row(officerId, approved + other.approved, rejected + other.rejected,
                    closed + other.closed, secondsToDecision + other.secondsToDecision);
        }
    }

    public static PerformanceReport of(Instant asOf, List<Row> rows, OfficerTeams teams) {
        Map<String, Row> byOfficer = new TreeMap<>();
        teams.officers().forEach(officer -> byOfficer.put(officer, Row.empty(officer)));
        rows.forEach(row -> byOfficer.merge(row.officerId(), row, Row::plus));

        Map<String, List<Row>> rowsByTeam = new TreeMap<>();
        byOfficer.values().forEach(row ->
                rowsByTeam.computeIfAbsent(teams.teamOf(row.officerId()), team -> new ArrayList<>()).add(row));

        List<TeamPerformance> teamPerformance = rowsByTeam.entrySet().stream()
                .map(team -> toTeam(team.getKey(), team.getValue()))
                .toList();
        return new PerformanceReport(asOf, teamPerformance);
    }

    private static TeamPerformance toTeam(String team, List<Row> officerRows) {
        Row total = officerRows.stream().reduce(Row.empty(team), Row::plus);
        List<OfficerPerformance> officers = officerRows.stream()
                .map(row -> new OfficerPerformance(row.officerId(), row.decided(), row.approved(), row.rejected(),
                        row.closed(), averageHours(row)))
                .toList();
        return new TeamPerformance(team, total.decided(), total.approved(), total.rejected(), total.closed(),
                averageHours(total), officers);
    }

    private static BigDecimal averageHours(Row row) {
        if (row.decided() == 0) {
            return null;
        }
        return BigDecimal.valueOf(row.secondsToDecision())
                .divide(BigDecimal.valueOf(row.decided() * SECONDS_PER_HOUR), 1, RoundingMode.HALF_UP);
    }
}
