package com.tripbudgetplanner.backend.expense.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.*;

// Validates the information entered for an expense.
public record ExpenseRequest(

    // Category is required and cannot exceed 50 characters.
    @NotBlank
    @Size(max = 50)
    String category,

    // Amount must be positive with up to 2 decimal places.
    @NotNull
    @DecimalMin("0.01")
    @Digits(integer = 10, fraction = 2)
    BigDecimal amount,

    // Date is required.
    @NotNull
    LocalDate expenseDate,

    // Description is optional.
    @Size(max = 255)
    String description

) {}
