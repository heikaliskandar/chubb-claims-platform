package com.chubb.claims.common.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

public record CurrentUser(String username, Role role) {

    private static final String ROLE_PREFIX = "ROLE_";

    public static CurrentUser from(Authentication authentication) {
        Role role = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith(ROLE_PREFIX))
                .map(authority -> Role.valueOf(authority.substring(ROLE_PREFIX.length())))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Authenticated user has no role"));
        return new CurrentUser(authentication.getName(), role);
    }

    public boolean isClaimant() {
        return role == Role.CLAIMANT;
    }
}
