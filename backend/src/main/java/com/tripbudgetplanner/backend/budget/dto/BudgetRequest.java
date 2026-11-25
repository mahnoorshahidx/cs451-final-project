package com.tripbudgetplanner.backend.budget.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.Map;

// What the Create Trip page sends: { totalBudget, currency, allocations: { "Flights": 1200, ... } }
public record BudgetRequest(

    @NotNull(message = "Total budget is required")
    @DecimalMin(value = "0.01", message = "Total budget must be greater than 0")
    @Digits(integer = 10, fraction = 2, message = "Total budget can have at most 2 decimal places")
    BigDecimal totalBudget,

    @NotBlank(message = "Currency is required")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter code like USD")
    String currency,

    @NotNull(message = "Allocations are required")
    Map<@NotBlank @Size(max = 50) String,
        @NotNull @DecimalMin(value = "0", message = "Amounts cannot be negative") BigDecimal> allocations,

    // Optional (UC6): the budget version the page loaded. If someone changed the budget
    // since then, the save is refused with 409 instead of overwriting their change.
    @PositiveOrZero(message = "Version cannot be negative")
    Long version
) {

    // The original form without a version (kept for existing code and tests)
    public BudgetRequest(BigDecimal totalBudget, String currency, Map<String, BigDecimal> allocations) {
        this(totalBudget, currency, allocations, null);
    }
}
