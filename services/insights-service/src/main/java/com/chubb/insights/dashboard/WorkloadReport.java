package com.chubb.insights.dashboard;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public record WorkloadReport(Instant asOf, long unassignedClaims, List<TeamWorkload> teams) {

    public record TeamWorkload(String team, long openClaims, List<OfficerWorkload> officers) {
    }

    public record OfficerWorkload(String officerId, long openClaims, Map<String, Long> claimsByStatus) {
    }

    public record Row(String officerId, String status, long claims) {
    }

    public static WorkloadReport of(Instant asOf, List<Row> rows, OfficerTeams teams) {
        long unassigned = 0;
        Map<String, Map<String, Long>> statusCountsByOfficer = new TreeMap<>();
        teams.officers().forEach(officer -> statusCountsByOfficer.put(officer, new TreeMap<>()));
        for (Row row : rows) {
            if (row.officerId() == null) {
                unassigned += row.claims();
            } else {
                statusCountsByOfficer.computeIfAbsent(row.officerId(), officer -> new TreeMap<>())
                        .merge(row.status(), row.claims(), Long::sum);
            }
        }

        Map<String, List<OfficerWorkload>> officersByTeam = new TreeMap<>();
        statusCountsByOfficer.forEach((officer, counts) -> {
            long open = counts.values().stream().mapToLong(Long::longValue).sum();
            officersByTeam.computeIfAbsent(teams.teamOf(officer), team -> new ArrayList<>())
                    .add(new OfficerWorkload(officer, open, counts));
        });

        List<TeamWorkload> teamWorkloads = officersByTeam.entrySet().stream()
                .map(team -> new TeamWorkload(team.getKey(),
                        team.getValue().stream().mapToLong(OfficerWorkload::openClaims).sum(),
                        team.getValue()))
                .toList();
        return new WorkloadReport(asOf, unassigned, teamWorkloads);
    }
}
