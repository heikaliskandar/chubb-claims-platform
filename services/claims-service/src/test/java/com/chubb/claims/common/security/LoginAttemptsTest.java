package com.chubb.claims.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class LoginAttemptsTest {

    private static final Instant START = Instant.parse("2026-11-20T09:00:00Z");

    private final AtomicReference<Instant> now = new AtomicReference<>(START);
    private final Clock clock = new Clock() {
        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    };
    private final LoginAttempts attempts = new LoginAttempts(3, Duration.ofMinutes(15), clock);

    @Test
    void locksAfterTheMaximumConsecutiveFailures() {
        fail("officer1", 2);
        assertThat(attempts.isLocked("officer1")).isFalse();

        fail("officer1", 1);
        assertThat(attempts.isLocked("officer1")).isTrue();
        assertThat(attempts.isLocked("officer2")).isFalse();
    }

    @Test
    void unlocksWhenTheLockoutExpires() {
        fail("officer1", 3);
        now.set(START.plus(Duration.ofMinutes(15)));
        assertThat(attempts.isLocked("officer1")).isFalse();
    }

    @Test
    void aSuccessfulSignInResetsTheCount() {
        fail("officer1", 2);
        attempts.onSuccess(new AuthenticationSuccessEvent(
                UsernamePasswordAuthenticationToken.authenticated("officer1", null, List.of())));
        fail("officer1", 2);
        assertThat(attempts.isLocked("officer1")).isFalse();
    }

    private void fail(String username, int times) {
        for (int i = 0; i < times; i++) {
            attempts.onFailure(new AuthenticationFailureBadCredentialsEvent(
                    UsernamePasswordAuthenticationToken.unauthenticated(username, "wrong"),
                    new BadCredentialsException("Bad credentials")));
        }
    }
}
