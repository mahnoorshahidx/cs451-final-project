package com.tripbudgetplanner.backend.dashboard;

import java.math.BigDecimal;
import java.util.Map;

// Data returned to the Dashboard frontend
public record DashboardSummaryResponse(
    long totalTrips,
    long upcomingTrips,
    Map<String, BigDecimal> budgetTotals,
    long connectedBankAccounts
) {}
