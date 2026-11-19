package com.chubb.claims.common.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LoginAttempts {

    private static final Logger AUDIT = LoggerFactory.getLogger("AUDIT");

    private final int maxFailures;
    private final Duration lockout;
    private final Clock clock;
    private final Map<String, Failures> failures = new ConcurrentHashMap<>();

    public LoginAttempts(int maxFailures, Duration lockout, Clock clock) {
        this.maxFailures = maxFailures;
        this.lockout = lockout;
        this.clock = clock;
    }

    public boolean isLocked(String username) {
        Failures entry = failures.get(username);
        if (entry == null || entry.lockedUntil() == null) {
            return false;
        }
        if (clock.instant().isBefore(entry.lockedUntil())) {
            return true;
        }
        failures.remove(username);
        return false;
    }

    @EventListener
    public void onFailure(AuthenticationFailureBadCredentialsEvent event) {
        String username = event.getAuthentication().getName();
        Failures updated = failures.merge(username, new Failures(1, null), (current, ignored) -> {
            int count = current.count() + 1;
            Instant lockedUntil = count >= maxFailures ? clock.instant().plus(lockout) : null;
            return new Failures(count, lockedUntil);
        });
        if (updated.lockedUntil() != null && updated.count() == maxFailures) {
            AUDIT.warn("action=LOCKOUT actor={} failures={} until={}", username, updated.count(), updated.lockedUntil());
        }
    }

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        failures.remove(event.getAuthentication().getName());
    }

    private record Failures(int count, Instant lockedUntil) {
    }
}
