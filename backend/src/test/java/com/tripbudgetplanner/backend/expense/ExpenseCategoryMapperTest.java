package com.tripbudgetplanner.backend.expense;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;

class ExpenseCategoryMapperTest {

    private static final List<String> STANDARD_BUDGET =
        List.of("Flights", "Accommodation", "Food", "Local transport", "Activities");

    @Test
    void mapsTheUseCaseNamesToBudgetCategories() {
        assertEquals("Flights", ExpenseCategoryMapper.matchBudgetCategory("Flight", STANDARD_BUDGET));
        assertEquals("Accommodation", ExpenseCategoryMapper.matchBudgetCategory("Hotel", STANDARD_BUDGET));
        assertEquals("Local transport", ExpenseCategoryMapper.matchBudgetCategory("Transportation", STANDARD_BUDGET));
        assertEquals("Activities", ExpenseCategoryMapper.matchBudgetCategory("activity", STANDARD_BUDGET));
        assertEquals("Local transport", ExpenseCategoryMapper.matchBudgetCategory("Rental car", STANDARD_BUDGET));
    }

    @Test
    void ignoresCaseAndExtraSpaces() {
        assertEquals("Local transport", ExpenseCategoryMapper.matchBudgetCategory("  LOCAL   transport ", STANDARD_BUDGET));
        assertEquals("Food", ExpenseCategoryMapper.matchBudgetCategory("food", STANDARD_BUDGET));
    }

    @Test
    void anExactCustomCategoryWinsOverAnAlias() {
        // The user made their own "Hotel" category, so "Hotel" expenses go there, not to Accommodation
        List<String> budget = List.of("Accommodation", "Hotel");

        assertEquals("Hotel", ExpenseCategoryMapper.matchBudgetCategory("hotel", budget));
    }

    @Test
    void returnsNullWhenTheBudgetHasNoMatch() {
        assertNull(ExpenseCategoryMapper.matchBudgetCategory("Shopping", STANDARD_BUDGET));
        // Alias known, but this budget has no Accommodation category
        assertNull(ExpenseCategoryMapper.matchBudgetCategory("Hotel", List.of("Food")));
    }

    @Test
    void displayNameUsesTheStandardNameForKnownAliases() {
        assertEquals("Accommodation", ExpenseCategoryMapper.displayName("hotel"));
        assertEquals("Gifts for family", ExpenseCategoryMapper.displayName(" Gifts  for family "));
    }
}
