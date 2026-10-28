package com.tripbudgetplanner.backend.recommendation;

import java.time.Month;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static java.time.Month.*;

// Hand-written travel data for the rule-based recommendations.
// Rough figures for a class project, not live prices. The AI provider replaces this.
final class DestinationProfiles {

    enum CostLevel { LOW, MEDIUM, HIGH }

    record Profile(
        String displayName,
        CostLevel costLevel,
        int flightUsdPerPerson,   // round trip, from somewhere in the US
        Set<Month> bestMonths,
        String bestNote,
        Set<Month> cheapMonths,
        String cheapNote
    ) {}

    // Keys are matched against the destination text (lowercase "contains")
    private static final Map<String, Profile> PROFILES = new LinkedHashMap<>();

    static {
        Profile japan = new Profile("Japan", CostLevel.HIGH, 1300,
            EnumSet.of(MARCH, APRIL, OCTOBER, NOVEMBER),
            "mild weather with cherry blossoms in spring and autumn leaves in fall",
            EnumSet.of(JANUARY, FEBRUARY, JUNE),
            "winter and the rainy season keep crowds and hotel prices down");
        PROFILES.put("kyoto", japan);
        PROFILES.put("tokyo", japan);
        PROFILES.put("osaka", japan);
        PROFILES.put("japan", japan);

        Profile portugal = new Profile("Portugal", CostLevel.MEDIUM, 850,
            EnumSet.of(APRIL, MAY, JUNE, SEPTEMBER, OCTOBER),
            "warm, sunny days without the peak-summer crowds",
            EnumSet.of(NOVEMBER, JANUARY, FEBRUARY, MARCH),
            "it's the low season, so flights and rooms are cheapest");
        PROFILES.put("lisbon", portugal);
        PROFILES.put("porto", portugal);
        PROFILES.put("portugal", portugal);

        Profile colorado = new Profile("Colorado", CostLevel.HIGH, 350,
            EnumSet.of(JUNE, JULY, AUGUST, SEPTEMBER),
            "hiking season with dry, sunny mountain weather",
            EnumSet.of(APRIL, MAY, OCTOBER, NOVEMBER),
            "the shoulder seasons fall between ski and summer crowds");
        PROFILES.put("denver", colorado);
        PROFILES.put("colorado", colorado);

        Profile morocco = new Profile("Morocco", CostLevel.LOW, 950,
            EnumSet.of(MARCH, APRIL, MAY, OCTOBER, NOVEMBER),
            "comfortable temperatures before and after the summer heat",
            EnumSet.of(JUNE, JULY, AUGUST, JANUARY),
            "summer heat and winter keep tourist demand low");
        PROFILES.put("marrakech", morocco);
        PROFILES.put("morocco", morocco);

        Profile paris = new Profile("Paris", CostLevel.HIGH, 900,
            EnumSet.of(APRIL, MAY, JUNE, SEPTEMBER, OCTOBER),
            "pleasant weather and long days",
            EnumSet.of(JANUARY, FEBRUARY, NOVEMBER),
            "winter is the quietest and cheapest season");
        PROFILES.put("paris", paris);
        PROFILES.put("france", paris);

        Profile london = new Profile("London", CostLevel.HIGH, 850,
            EnumSet.of(MAY, JUNE, SEPTEMBER),
            "the mildest, driest stretch of the year",
            EnumSet.of(JANUARY, FEBRUARY, NOVEMBER),
            "post-holiday winter brings the lowest hotel rates");
        PROFILES.put("london", london);
        PROFILES.put("england", london);

        Profile nyc = new Profile("New York", CostLevel.HIGH, 350,
            EnumSet.of(APRIL, MAY, SEPTEMBER, OCTOBER),
            "comfortable temperatures for walking the city",
            EnumSet.of(JANUARY, FEBRUARY),
            "after the holidays, hotel prices drop sharply");
        PROFILES.put("new york", nyc);
        PROFILES.put("nyc", nyc);
    }

    // Used when the destination isn't in the table: shoulder seasons are a safe default
    private static final Profile DEFAULT = new Profile(null, CostLevel.MEDIUM, 700,
        EnumSet.of(APRIL, MAY, SEPTEMBER, OCTOBER),
        "shoulder-season weather is usually mild and crowds are smaller",
        EnumSet.of(JANUARY, FEBRUARY, NOVEMBER),
        "outside holidays and summer, demand and prices are usually lowest");

    private DestinationProfiles() {}

    static Profile find(String destination) {
        String text = destination == null ? "" : destination.toLowerCase(Locale.ROOT);
        return PROFILES.entrySet().stream()
            .filter(entry -> text.contains(entry.getKey()))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElse(DEFAULT);
    }

    static boolean isKnown(Profile profile) {
        return profile != DEFAULT;
    }
}
