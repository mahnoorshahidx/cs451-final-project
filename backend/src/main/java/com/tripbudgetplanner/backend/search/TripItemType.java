package com.tripbudgetplanner.backend.search;

// The four kinds of things you can search for and add to a trip (UC6).
// itineraryCategory matches the categories the itinerary editor already uses;
// budgetCategory matches the standard budget categories.
public enum TripItemType {

    FLIGHT("Transportation", "Flights"),
    HOTEL("Accommodation", "Accommodation"),
    RESTAURANT("Food & Drink", "Food"),
    ACTIVITY("Activity", "Activities");

    private final String itineraryCategory;
    private final String budgetCategory;

    TripItemType(String itineraryCategory, String budgetCategory) {
        this.itineraryCategory = itineraryCategory;
        this.budgetCategory = budgetCategory;
    }

    public String getItineraryCategory() { return itineraryCategory; }
    public String getBudgetCategory() { return budgetCategory; }
}
