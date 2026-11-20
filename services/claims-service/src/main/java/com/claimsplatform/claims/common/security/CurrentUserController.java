package com.claimsplatform.claims.common.security;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Current user")
public class CurrentUserController {

    @GetMapping("/api/v1/me")
    @Operation(summary = "Returns the authenticated user and their role")
    public CurrentUser me(Authentication authentication) {
        return CurrentUser.from(authentication);
    }
}
