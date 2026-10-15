package com.tripbudgetplanner.backend.bank.dto;

import com.tripbudgetplanner.backend.bank.AccountType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

// What the frontend sends when connecting a bank account
public record ConnectBankAccountRequest(
    @NotNull(message = "Account type is required")
    AccountType accountType,

    @NotBlank(message = "Routing number is required")
    @Pattern(regexp = "^(\\d{9})?$", message = "Invalid routing number")
    String routingNumber,

    @NotBlank(message = "Account number is required")
    @Pattern(regexp = "^(\\d{4,17})?$", message = "Invalid account number")
    String accountNumber
) {}