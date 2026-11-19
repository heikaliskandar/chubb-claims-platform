package com.chubb.claims.claim.application;

import com.chubb.claims.claim.domain.ApprovalLimit;
import com.chubb.claims.common.security.Position;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorityLimitsTest {

    private final AuthorityLimits limits = new AuthorityLimits(Map.of(
            Position.CLAIMS_OFFICER, Map.of("SGD", new BigDecimal("10000"), "MYR", new BigDecimal("30000"))));

    @Test
    void limitsAreLookedUpPerCurrencyWithoutConversion() {
        assertThat(limits.limitFor(Position.CLAIMS_OFFICER, "SGD")).isEqualTo(ApprovalLimit.upTo(new BigDecimal("10000")));
        assertThat(limits.limitFor(Position.CLAIMS_OFFICER, "MYR")).isEqualTo(ApprovalLimit.upTo(new BigDecimal("30000")));
    }

    @Test
    void anUnconfiguredCurrencyOrPositionGrantsNoAuthority() {
        assertThat(limits.limitFor(Position.CLAIMS_OFFICER, "IDR")).isEqualTo(ApprovalLimit.NONE);
        assertThat(limits.limitFor(Position.TEAM_LEAD, "SGD")).isEqualTo(ApprovalLimit.NONE);
        assertThat(limits.limitFor(null, "SGD")).isEqualTo(ApprovalLimit.NONE);
    }

    @Test
    void theClaimsManagerHasUnlimitedAuthority() {
        assertThat(limits.limitFor(Position.CLAIMS_MANAGER, "IDR")).isEqualTo(ApprovalLimit.UNLIMITED);
    }
}
