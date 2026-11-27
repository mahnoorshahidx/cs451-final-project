package com.tripbudgetplanner.backend.itinerary.options;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

// A draft option as the comparison screen needs it: totals up front, then the activities
public record ItineraryOptionResponse(
    Long id,
    String style,
    String label,
    String summary,
    LocalDate startDate,
    LocalDate endDate,
    BigDecimal totalCost,
    String currency,
    int activityCount,
    // Cost per category (Food, Sightseeing...), for comparing where the money goes
    Map<String, BigDecimal> costByCategory,
    Instant createdAt,
    List<Activity> activities
) {
    public record Activity(LocalDate activityDate, LocalTime startTime, LocalTime endTime, String title,
                           String category, String location, String notes, BigDecimal estimatedCost,
                           String costCurrency) {}

    static ItineraryOptionResponse of(ItineraryOption option) {
        Map<String, BigDecimal> byCategory = new TreeMap<>();
        List<Activity> activities = option.getActivities().stream().map(a -> {
            if (a.getEstimatedCost() != null) {
                byCategory.merge(a.getCategory(), a.getEstimatedCost(), BigDecimal::add);
            }
            return new Activity(a.getActivityDate(), a.getStartTime(), a.getEndTime(), a.getTitle(),
                a.getCategory(), a.getLocation(), a.getNotes(), a.getEstimatedCost(), a.getCostCurrency());
        }).toList();

        return new ItineraryOptionResponse(option.getId(), option.getStyle(), option.getLabel(), option.getSummary(),
            option.getStartDate(), option.getEndDate(), option.getTotalCost(), option.getCurrency(),
            activities.size(), byCategory, option.getCreatedAt(), activities);
    }
}
