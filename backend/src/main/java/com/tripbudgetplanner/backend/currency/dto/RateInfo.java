package com.tripbudgetplanner.backend.currency.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

// The exchange rate used for a response: 1 "from" = rate "to".
// source is "live" or "fallback"; notice warns when demo rates were used.
public record RateInfo(
    String from,
    String to,
    BigDecimal rate,
    LocalDate rateDate,
    String source,
    String notice
) {

    public static RateInfo of(ConversionResponse conversion) {
        return new RateInfo(conversion.from(), conversion.to(), conversion.rate(),
            conversion.rateDate(), conversion.source(), conversion.notice());
    }
}
