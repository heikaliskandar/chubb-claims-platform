package com.chubb.claims.common.security;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.List;

public record CurrentUser(String username, Role role, Position position) {

    public static final String ROLE_PREFIX = "ROLE_";
    public static final String POSITION_PREFIX = "POSITION_";

    public static CurrentUser from(Authentication authentication) {
        List<String> authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        Role role = authorities.stream()
                .filter(authority -> authority.startsWith(ROLE_PREFIX))
                .map(authority -> Role.valueOf(authority.substring(ROLE_PREFIX.length())))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Authenticated user has no role"));
        Position position = authorities.stream()
                .filter(authority -> authority.startsWith(POSITION_PREFIX))
                .map(authority -> Position.valueOf(authority.substring(POSITION_PREFIX.length())))
                .findFirst()
                .orElse(null);
        return new CurrentUser(authentication.getName(), role, position);
    }

    @JsonIgnore
    public boolean isClaimant() {
        return role == Role.CLAIMANT;
    }

    @JsonIgnore
    public boolean isOfficer() {
        return role == Role.OFFICER;
    }
}
