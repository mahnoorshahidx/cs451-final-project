package com.tripbudgetplanner.backend.savings.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

// Log money put aside for a trip (simulated, not a real transfer)
public record ContributionRequest(

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    @Digits(integer = 10, fraction = 2, message = "At most 2 decimal places")
    BigDecimal amount,

    // Optional: defaults to today
    LocalDate date,

    @Size(max = 255, message = "Description is too long")
    String description,

    // Optional: must be one of the user's connected accounts
    Long bankAccountId
) {}
