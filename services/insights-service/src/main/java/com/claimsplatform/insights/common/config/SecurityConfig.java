package com.claimsplatform.insights.common.config;

import com.claimsplatform.insights.common.security.DemoUserProperties;
import com.claimsplatform.insights.common.security.LoginAttempts;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(DemoUserProperties.class)
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
            "/actuator/health/**", "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/error"
    };

    private static final int MAX_FAILED_LOGINS = 5;
    private static final Duration LOCKOUT = Duration.ofMinutes(15);

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) throws Exception {
        AuthenticationEntryPoint unauthenticated =
                (request, response, exception) -> exceptionResolver.resolveException(request, response, null, exception);
        AccessDeniedHandler forbidden =
                (request, response, exception) -> exceptionResolver.resolveException(request, response, null, exception);

        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated())
                .httpBasic(basic -> basic.authenticationEntryPoint(unauthenticated))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(unauthenticated)
                        .accessDeniedHandler(forbidden))
                .build();
    }

    @Bean
    LoginAttempts loginAttempts() {
        return new LoginAttempts(MAX_FAILED_LOGINS, LOCKOUT, Clock.systemUTC());
    }

    @Bean
    UserDetailsService demoUsers(DemoUserProperties properties, PasswordEncoder passwordEncoder,
                                 LoginAttempts loginAttempts) {
        String encodedPassword = passwordEncoder.encode(properties.password());
        List<UserDetails> users = properties.users().entrySet().stream()
                .map(user -> User.withUsername(user.getKey())
                        .password(encodedPassword)
                        .roles(user.getValue().name())
                        .build())
                .toList();
        InMemoryUserDetailsManager accounts = new InMemoryUserDetailsManager(users);
        return username -> User.withUserDetails(accounts.loadUserByUsername(username))
                .accountLocked(loginAttempts.isLocked(username))
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
