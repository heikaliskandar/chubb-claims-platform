package com.claimsplatform.claims.common.security;

public enum Position {
    CLAIMS_OFFICER,
    SENIOR_CLAIMS_OFFICER,
    TEAM_LEAD,
    CLAIMS_MANAGER;

    public boolean hasUnlimitedAuthority() {
        return this == CLAIMS_MANAGER;
    }
}
