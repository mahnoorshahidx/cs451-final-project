package com.tripbudgetplanner.backend.expense.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

// Returns expense details to the frontend.
public record ExpenseResponse(
    Long id,
    Long tripId,
    String category,
    BigDecimal amount,
    LocalDate expenseDate,
    String description
) {}
