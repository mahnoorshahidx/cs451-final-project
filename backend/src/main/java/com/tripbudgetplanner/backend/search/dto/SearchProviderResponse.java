package com.tripbudgetplanner.backend.search.dto;

import java.util.List;

// Which search provider is active, so the frontend can show a "demo data" banner
public record SearchProviderResponse(
    String provider,
    boolean live,
    List<String> types,
    String notice
) {}
