package com.tripbudgetplanner.backend.currency.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

// Result of converting an amount from one currency to another.
// rate:     1 unit of "from" = rate units of "to" (rounded to 6 decimals for display)
// rateDate: the day the rates were published (null for demo rates)
// source:   "live" (Frankfurter) or "fallback" (fixed demo rates)
// notice:   a warning to show the user when demo rates were used, otherwise null
public record ConversionResponse(
    String from,
    String to,
    BigDecimal amount,
    BigDecimal rate,
    BigDecimal convertedAmount,
    LocalDate rateDate,
    String source,
    String notice
) {}
