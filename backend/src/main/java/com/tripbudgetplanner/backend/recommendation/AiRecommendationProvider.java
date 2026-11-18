package com.tripbudgetplanner.backend.recommendation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import com.tripbudgetplanner.backend.ai.AiClientException;
import com.tripbudgetplanner.backend.ai.AnthropicClient;
import com.tripbudgetplanner.backend.recommendation.dto.BudgetRecommendation;
import com.tripbudgetplanner.backend.recommendation.dto.BudgetRecommendationRequest;
import com.tripbudgetplanner.backend.recommendation.dto.TimingRecommendation;
import com.tripbudgetplanner.backend.recommendation.dto.TimingRecommendationRequest;

import tools.jackson.databind.JsonNode;

// AI recommendations for any destination, using Claude.
// @Primary, so the controller gets this one instead of the rule-based provider.
//
// - No API key set: uses RuleBasedRecommendationProvider (source "rules"), so the app runs without a key.
// - AI call fails, times out or returns something that doesn't pass validation:
//   also falls back to the rule-based answer, so the user always gets a recommendation.
// - Otherwise returns the AI answer with source "ai".
//
// The AI only picks dates and estimates category amounts. Totals, the 10% buffer and the
// low/high range are calculated here the same way as the rule-based provider, so both
// providers return numbers the frontend can treat the same way.
@Service
@Primary
public class AiRecommendationProvider implements RecommendationProvider {

    private static final Logger log = LoggerFactory.getLogger(AiRecommendationProvider.class);

    static final String SOURCE = "ai";

    // Same categories as the rule-based provider, in the same order
    static final List<String> CATEGORIES = List.of("Flights", "Accommodation", "Food", "Local transport", "Activities");

    private static final BigDecimal BUFFER = new BigDecimal("1.10");   // 10% left unallocated
    private static final BigDecimal LOW_FACTOR = new BigDecimal("0.85");
    private static final BigDecimal HIGH_FACTOR = new BigDecimal("1.30");

    // Plain-English meaning of the trade-off codes the wizard sends
    private static final Map<String, String> TRADE_OFFS = Map.of(
        "budgetHotels", "OK with budget hotels",
        "sharedRooms", "OK with shared rooms or hostels",
        "lowCostMeals", "OK with low-cost meals (street food, groceries)",
        "longLayovers", "OK with long layovers for cheaper flights",
        "lowerCostActivities", "prefers lower-cost activities",
        "skipPaidActivities", "wants to skip most paid activities"
    );

    private final AnthropicClient ai;
    private final RuleBasedRecommendationProvider fallback;

    public AiRecommendationProvider(AnthropicClient ai, RuleBasedRecommendationProvider fallback) {
        this.ai = ai;
        this.fallback = fallback;
    }

    // ---------------- Timing ----------------

    @Override
    public TimingRecommendation recommendTiming(TimingRecommendationRequest request) {
        int nights = request.nights();
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        LocalDate earliest = request.windowStart().isBefore(tomorrow) ? tomorrow : request.windowStart();
        LocalDate latestStart = request.windowEnd().minusDays(nights);

        // Same check and message as the rule-based provider: no point asking the AI for an impossible window
        if (latestStart.isBefore(earliest)) {
            throw new IllegalArgumentException(
                "Your travel window is shorter than " + nights + " night" + (nights == 1 ? "" : "s") + ". Widen it or shorten the trip.");
        }

        if (!ai.isEnabled()) {
            return fallback.recommendTiming(request);
        }

        boolean cheapest = "cheapest".equals(request.mode());
        String system = """
            You are a travel planning assistant inside a trip budgeting app.
            Pick the best start date for a trip within the traveler's window.
            Reply with only a JSON object, no other text:
            {"startDate": "YYYY-MM-DD", "reason": "one or two friendly sentences"}
            The reason should mention the month and why it's a good choice. Keep it under 250 characters.
            """;
        String user = """
            Destination: %s
            Goal: %s
            Trip length: %d nights
            The start date must be between %s and %s (inclusive).
            """.formatted(
                request.destination().trim(),
                cheapest
                    ? "the cheapest time to go (low season, lower fares and hotel prices; midweek departures if helpful)"
                    : "the best time to go (good weather, good conditions, reasonable crowds)",
                nights, earliest, latestStart);

        try {
            JsonNode json = ai.parseJsonObject(ai.complete(system, user));

            LocalDate start = LocalDate.parse(json.path("startDate").asString());
            if (start.isBefore(earliest) || start.isAfter(latestStart)) {
                throw new AiClientException("AI picked " + start + ", outside " + earliest + " to " + latestStart + ".");
            }

            String reason = cleanText(json.path("reason").asString(), 300);
            if (reason.isEmpty()) {
                throw new AiClientException("AI gave no reason.");
            }

            return new TimingRecommendation(start, start.plusDays(nights), reason, SOURCE);
        } catch (AiClientException | DateTimeParseException e) {
            log.warn("AI timing recommendation failed, using rules instead: {}", e.getMessage());
            return fallback.recommendTiming(request);
        }
    }

    // ---------------- Budget ----------------

    @Override
    public BudgetRecommendation recommendBudget(BudgetRecommendationRequest request) {
        if (!ai.isEnabled()) {
            return fallback.recommendBudget(request);
        }

        int nights = request.nights();
        int travelers = request.travelers();
        int rooms = (travelers + 1) / 2;
        String currency = request.currency();

        List<String> tradeOffs = request.tradeOffs() == null ? List.of() : request.tradeOffs();
        List<String> tradeOffText = tradeOffs.stream()
            .map(TRADE_OFFS::get)
            .filter(t -> t != null)
            .toList();

        String system = """
            You are a travel budget assistant inside a trip budgeting app.
            Estimate realistic total costs for a trip, for the whole group, in the requested currency.
            Reply with only a JSON object, no other text:
            {
              "allocations": {"Flights": 0, "Accommodation": 0, "Food": 0, "Local transport": 0, "Activities": 0},
              "assumptions": ["short sentence", "..."]
            }
            Use exactly those five category names. Amounts are whole numbers, 0 or more, with no currency symbols.
            Flights are round trip from the United States for every traveler.
            Accommodation is for the given number of rooms and nights.
            Give 2 to 4 assumptions explaining the main cost drivers (season, price level, trade-offs).
            Each assumption is one short sentence under 150 characters.
            """;
        String user = """
            Destination: %s
            Start date: %s
            Nights: %d
            Travelers: %d
            Rooms: %d
            Currency: %s
            Traveler is OK with: %s
            """.formatted(
                request.destination().trim(),
                request.startDate() == null ? "not chosen yet" : request.startDate().toString(),
                nights, travelers, rooms, currency,
                tradeOffText.isEmpty() ? "no particular trade-offs" : String.join("; ", tradeOffText));

        try {
            JsonNode json = ai.parseJsonObject(ai.complete(system, user));
            Map<String, BigDecimal> allocations = readAllocations(json.path("allocations"));

            BigDecimal subtotal = allocations.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            if (subtotal.signum() <= 0) {
                throw new AiClientException("AI estimated a total of zero.");
            }

            BigDecimal recommended = roundUpToNearest(subtotal.multiply(BUFFER), 50);
            BigDecimal low = roundToNearest(subtotal.multiply(LOW_FACTOR), 50);
            BigDecimal high = roundUpToNearest(subtotal.multiply(HIGH_FACTOR), 50);

            List<String> assumptions = new ArrayList<>();
            assumptions.add(travelers + " traveler" + (travelers == 1 ? "" : "s") + ", " + nights + " night" + (nights == 1 ? "" : "s")
                + ", " + rooms + " room" + (rooms == 1 ? "" : "s") + ".");
            JsonNode aiAssumptions = json.path("assumptions");
            for (int i = 0; i < aiAssumptions.size(); i++) {
                String text = cleanText(aiAssumptions.get(i).asString(), 200);
                if (!text.isEmpty() && assumptions.size() < 6) {
                    assumptions.add(text);
                }
            }
            assumptions.add("AI estimate, not live prices. Check current fares and rates before booking.");
            assumptions.add("About 10% is left unallocated as a buffer.");

            return new BudgetRecommendation(recommended, low, high, currency, allocations, assumptions, SOURCE);
        } catch (AiClientException e) {
            log.warn("AI budget recommendation failed, using rules instead: {}", e.getMessage());
            return fallback.recommendBudget(request);
        }
    }

    // ---------------- Helpers ----------------

    // All five categories must be present as non-negative numbers; anything else is rejected
    private static Map<String, BigDecimal> readAllocations(JsonNode node) {
        if (!node.isObject()) {
            throw new AiClientException("AI reply had no allocations.");
        }
        Map<String, BigDecimal> allocations = new LinkedHashMap<>();
        for (String category : CATEGORIES) {
            JsonNode value = node.get(category);
            if (value == null || !value.isNumber()) {
                throw new AiClientException("AI reply is missing a number for " + category + ".");
            }
            BigDecimal amount = value.decimalValue().setScale(0, RoundingMode.HALF_UP);
            if (amount.signum() < 0) {
                throw new AiClientException("AI gave a negative amount for " + category + ".");
            }
            allocations.put(category, amount);
        }
        return allocations;
    }

    // Single line, trimmed, capped in length
    private static String cleanText(String text, int maxLength) {
        String clean = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        return clean.length() <= maxLength ? clean : clean.substring(0, maxLength - 1).trim() + "…";
    }

    private static BigDecimal roundToNearest(BigDecimal value, int step) {
        BigDecimal s = BigDecimal.valueOf(step);
        return value.divide(s, 0, RoundingMode.HALF_UP).multiply(s);
    }

    private static BigDecimal roundUpToNearest(BigDecimal value, int step) {
        BigDecimal s = BigDecimal.valueOf(step);
        return value.divide(s, 0, RoundingMode.CEILING).multiply(s);
    }
}