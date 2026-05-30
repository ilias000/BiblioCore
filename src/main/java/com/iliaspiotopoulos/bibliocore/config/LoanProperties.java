package com.iliaspiotopoulos.bibliocore.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "bibliocore.loan")
public record LoanProperties(
        int defaultLimit,
        int periodDays,
        BigDecimal finePerDay,
        String overdueScanCron
) {
    public LoanProperties {
        if (defaultLimit <= 0) {
            defaultLimit = 3;
        }
        if (periodDays <= 0) {
            periodDays = 14;
        }
        if (finePerDay == null || finePerDay.compareTo(BigDecimal.ZERO) < 0) {
            finePerDay = new BigDecimal("0.20");
        }
        if (overdueScanCron == null || overdueScanCron.isBlank()) {
            overdueScanCron = "0 0 1 * * *";
        }
    }
}