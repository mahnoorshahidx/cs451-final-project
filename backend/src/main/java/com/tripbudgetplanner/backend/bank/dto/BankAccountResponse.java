package com.tripbudgetplanner.backend.bank.dto;

import com.tripbudgetplanner.backend.bank.AccountType;

// What the frontend gets back. Never includes the full account number.
public record BankAccountResponse(
    Long id,
    AccountType accountType,
    String routingNumber,
    String accountLast4
) {}