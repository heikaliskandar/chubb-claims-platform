package com.chubb.insights.dashboard;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public record ExposureReport(Instant asOf, List<ExposureLine> lines, List<CurrencyTotal> totalsByCurrency) {

    public record ExposureLine(String market, String claimType, String currency, long openClaims,
                               BigDecimal outstandingReserve) {
    }

    public record CurrencyTotal(String currency, long openClaims, BigDecimal outstandingReserve) {

        CurrencyTotal plus(CurrencyTotal other) {
            if (!currency.equals(other.currency)) {
                throw new IllegalArgumentException("Cannot add " + other.currency + " to " + currency);
            }
            return new CurrencyTotal(currency, openClaims + other.openClaims,
                    outstandingReserve.add(other.outstandingReserve));
        }
    }

    public static ExposureReport of(Instant asOf, List<ExposureLine> lines) {
        Map<String, CurrencyTotal> totals = new TreeMap<>();
        for (ExposureLine line : lines) {
            totals.merge(line.currency(),
                    new CurrencyTotal(line.currency(), line.openClaims(), line.outstandingReserve()),
                    CurrencyTotal::plus);
        }
        return new ExposureReport(asOf, List.copyOf(lines), List.copyOf(totals.values()));
    }
}
