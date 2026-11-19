package com.chubb.claims.claim.domain;

public final class ClaimNumber {

    private ClaimNumber() {
    }

    public static String format(Market market, int year, long sequence) {
        return "CLM-%s-%d-%06d".formatted(market, year, sequence);
    }
}
