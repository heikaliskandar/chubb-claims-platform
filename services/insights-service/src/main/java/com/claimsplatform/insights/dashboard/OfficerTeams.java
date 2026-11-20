package com.claimsplatform.insights.dashboard;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;
import java.util.Set;

@ConfigurationProperties("app")
public record OfficerTeams(Map<String, String> officerTeams) {

    public static final String UNMAPPED_TEAM = "Unmapped";

    public OfficerTeams {
        officerTeams = officerTeams == null ? Map.of() : Map.copyOf(officerTeams);
    }

    public String teamOf(String officerId) {
        return officerTeams.getOrDefault(officerId, UNMAPPED_TEAM);
    }

    public Set<String> officers() {
        return officerTeams.keySet();
    }
}
