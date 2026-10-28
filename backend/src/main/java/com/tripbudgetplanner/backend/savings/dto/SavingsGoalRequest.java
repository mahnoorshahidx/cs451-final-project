package com.tripbudgetplanner.backend.savings.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

// Create or update a trip's savings goal
public record SavingsGoalRequest(

    @NotNull(message = "Savings target is required")
    @DecimalMin(value = "0.01", message = "Savings target must be greater than 0")
    @Digits(integer = 10, fraction = 2, message = "At most 2 decimal places")
    BigDecimal targetAmount,

    // Optional: defaults to 0
    @DecimalMin(value = "0", message = "Amount already saved can't be negative")
    @Digits(integer = 10, fraction = 2, message = "At most 2 decimal places")
    BigDecimal initialSavedAmount,

    @NotNull(message = "Target date is required")
    LocalDate targetDate,

    // Optional
    @DecimalMin(value = "0", message = "Monthly plan can't be negative")
    @Digits(integer = 10, fraction = 2, message = "At most 2 decimal places")
    BigDecimal plannedMonthlyContribution
) {}
