package com.chubb.claims.common.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.Map;

@Validated
@ConfigurationProperties("app.security.demo-users")
public record DemoUserProperties(@NotBlank String password, @NotEmpty Map<String, Role> users) {
}
