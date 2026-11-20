package com.claimsplatform.claims.common.config;

import com.claimsplatform.claims.claim.application.AuthorityLimits;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AuthorityLimits.class)
public class AuthorityConfig {
}
