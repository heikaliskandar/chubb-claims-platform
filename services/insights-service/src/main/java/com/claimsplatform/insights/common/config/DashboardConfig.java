package com.claimsplatform.insights.common.config;

import com.claimsplatform.insights.dashboard.OfficerTeams;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(OfficerTeams.class)
public class DashboardConfig {
}
