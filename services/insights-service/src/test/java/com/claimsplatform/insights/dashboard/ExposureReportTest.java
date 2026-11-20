package com.claimsplatform.insights.dashboard;

import com.claimsplatform.insights.dashboard.ExposureReport.CurrencyTotal;
import com.claimsplatform.insights.dashboard.ExposureReport.ExposureLine;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExposureReportTest {

    @Test
    void totalsAreKeptPerCurrencyAndNeverMixed() {
        ExposureReport report = ExposureReport.of(null, List.of(
                new ExposureLine("SG", "MOTOR", "SGD", 2, new BigDecimal("3000.00")),
                new ExposureLine("SG", "PROPERTY", "SGD", 1, new BigDecimal("12500.50")),
                new ExposureLine("ID", "MOTOR", "IDR", 3, new BigDecimal("21000000.00")),
                new ExposureLine("MY", "MOTOR", "MYR", 1, new BigDecimal("4200.00"))));

        assertThat(report.totalsByCurrency()).containsExactly(
                new CurrencyTotal("IDR", 3, new BigDecimal("21000000.00")),
                new CurrencyTotal("MYR", 1, new BigDecimal("4200.00")),
                new CurrencyTotal("SGD", 3, new BigDecimal("15500.50")));
    }

    @Test
    void keepsEveryMarketAndClaimTypeLine() {
        List<ExposureLine> lines = List.of(
                new ExposureLine("HK", "MOTOR", "HKD", 1, new BigDecimal("100.00")),
                new ExposureLine("HK", "PROPERTY", "HKD", 1, new BigDecimal("200.00")));

        assertThat(ExposureReport.of(null, lines).lines()).isEqualTo(lines);
    }

    @Test
    void anEmptyBookHasNoExposure() {
        ExposureReport report = ExposureReport.of(null, List.of());

        assertThat(report.lines()).isEmpty();
        assertThat(report.totalsByCurrency()).isEmpty();
    }

    @Test
    void refusesToAddDifferentCurrencies() {
        CurrencyTotal sgd = new CurrencyTotal("SGD", 1, new BigDecimal("10.00"));
        CurrencyTotal myr = new CurrencyTotal("MYR", 1, new BigDecimal("10.00"));

        assertThatThrownBy(() -> sgd.plus(myr)).isInstanceOf(IllegalArgumentException.class);
    }
}
