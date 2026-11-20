package com.claimsplatform.claims.claim.application;

import com.claimsplatform.claims.claim.domain.ApprovalLimit;
import com.claimsplatform.claims.common.security.Position;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.Map;

@ConfigurationProperties("app.authority")
public record AuthorityLimits(Map<Position, Map<String, BigDecimal>> limits) {

    public AuthorityLimits {
        limits = limits == null ? Map.of() : Map.copyOf(limits);
    }

    public ApprovalLimit limitFor(Position position, String currency) {
        if (position == null) {
            return ApprovalLimit.NONE;
        }
        if (position.hasUnlimitedAuthority()) {
            return ApprovalLimit.UNLIMITED;
        }
        BigDecimal maximum = limits.getOrDefault(position, Map.of()).get(currency);
        return maximum == null ? ApprovalLimit.NONE : ApprovalLimit.upTo(maximum);
    }
}
