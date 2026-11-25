package com.tripbudgetplanner.backend.budget.dto;

import java.math.BigDecimal;

import com.tripbudgetplanner.backend.currency.dto.RateInfo;

// What adding an item to the budget changed (UC6, updateBudget=true).
// All amounts are in "currency", the budget's currency.
// conversion is null when the item's currency already matched the budget's.
public record BudgetChange(
    String category,
    BigDecimal amountAdded,
    String currency,
    BigDecimal allocationBefore,
    BigDecimal allocationAfter,
    BigDecimal totalBefore,
    BigDecimal totalAfter,
    boolean budgetCreated,
    long budgetVersion,
    RateInfo conversion
) {}
