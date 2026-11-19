package com.chubb.claims.claim.domain;

public enum Market {
    SG("SGD"),
    MY("MYR"),
    HK("HKD"),
    TH("THB"),
    ID("IDR"),
    PH("PHP");

    private final String currency;

    Market(String currency) {
        this.currency = currency;
    }

    public String currency() {
        return currency;
    }
}
