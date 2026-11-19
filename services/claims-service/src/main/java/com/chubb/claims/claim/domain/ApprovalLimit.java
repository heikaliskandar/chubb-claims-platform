package com.chubb.claims.claim.domain;

import java.math.BigDecimal;

public record ApprovalLimit(BigDecimal maximum) {

    public static final ApprovalLimit UNLIMITED = new ApprovalLimit(null);
    public static final ApprovalLimit NONE = new ApprovalLimit(BigDecimal.ZERO);

    public static ApprovalLimit upTo(BigDecimal maximum) {
        return new ApprovalLimit(maximum);
    }

    public boolean covers(BigDecimal amount) {
        return maximum == null || amount.compareTo(maximum) <= 0;
    }
}
