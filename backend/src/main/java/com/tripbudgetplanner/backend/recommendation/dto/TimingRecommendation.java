package com.tripbudgetplanner.backend.recommendation.dto;

import java.time.LocalDate;

// source: "rules" today, "ai" once the AI provider is in
public record TimingRecommendation(
    LocalDate startDate,
    LocalDate endDate,
    String reason,
    String source
) {}
