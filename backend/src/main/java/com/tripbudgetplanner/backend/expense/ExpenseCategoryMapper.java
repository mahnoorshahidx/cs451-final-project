package com.tripbudgetplanner.backend.expense;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// Matches expense categories (free text, e.g. "Hotel") to budget categories (e.g. "Accommodation").
//
// Budget categories come from the Create Trip / Edit Trip pages: Flights, Accommodation, Food,
// Local transport, Activities, plus any custom ones the user adds.
// Expense categories are typed by the user, so the same thing can have different names
// (the use case lists flight, hotel, food, transportation, activities, shopping).
public final class ExpenseCategoryMapper {

    // The standard budget categories, in the order the frontend shows them
    public static final List<String> STANDARD_CATEGORIES =
        List.of("Flights", "Accommodation", "Food", "Local transport", "Activities");

    // Other names people use for the standard categories (keys are lowercase)
    private static final Map<String, String> ALIASES = Map.ofEntries(
        Map.entry("flight", "Flights"),
        Map.entry("flights", "Flights"),
        Map.entry("airfare", "Flights"),
        Map.entry("hotel", "Accommodation"),
        Map.entry("hotels", "Accommodation"),
        Map.entry("lodging", "Accommodation"),
        Map.entry("accommodation", "Accommodation"),
        Map.entry("accommodations", "Accommodation"),
        Map.entry("food", "Food"),
        Map.entry("meals", "Food"),
        Map.entry("dining", "Food"),
        Map.entry("restaurant", "Food"),
        Map.entry("restaurants", "Food"),
        Map.entry("transportation", "Local transport"),
        Map.entry("transport", "Local transport"),
        Map.entry("local transport", "Local transport"),
        Map.entry("local transportation", "Local transport"),
        Map.entry("rental car", "Local transport"),
        Map.entry("car rental", "Local transport"),
        Map.entry("activity", "Activities"),
        Map.entry("activities", "Activities")
    );

    private ExpenseCategoryMapper() {}

    // "  Local   Transport " -> "local transport"
    static String normalize(String category) {
        return category == null ? "" : category.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    // The standard budget name for a known alias ("hotel" -> "Accommodation"), or null
    public static String standardName(String category) {
        return ALIASES.get(normalize(category));
    }

    // Finds the budget category this expense belongs to, or null if the budget has none.
    // 1. Same name, ignoring case and extra spaces ("food" matches "Food").
    //    This also covers custom budget categories like "Shopping".
    // 2. A known alias whose standard name is in the budget ("Hotel" -> "Accommodation").
    public static String matchBudgetCategory(String expenseCategory, Collection<String> budgetCategories) {
        String wanted = normalize(expenseCategory);
        for (String budgetCategory : budgetCategories) {
            if (normalize(budgetCategory).equals(wanted)) {
                return budgetCategory;
            }
        }

        String standard = ALIASES.get(wanted);
        if (standard != null) {
            for (String budgetCategory : budgetCategories) {
                if (normalize(budgetCategory).equals(normalize(standard))) {
                    return budgetCategory;
                }
            }
        }
        return null;
    }

    // Name to show for spending that has no budget category.
    // Known aliases use the standard name ("hotel" -> "Accommodation"); anything else keeps its own name.
    public static String displayName(String expenseCategory) {
        String standard = standardName(expenseCategory);
        return standard != null ? standard : expenseCategory.trim().replaceAll("\\s+", " ");
    }
}
