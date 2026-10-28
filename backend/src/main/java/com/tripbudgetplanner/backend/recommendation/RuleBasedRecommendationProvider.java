package com.tripbudgetplanner.backend.recommendation;

import com.tripbudgetplanner.backend.recommendation.DestinationProfiles.CostLevel;
import com.tripbudgetplanner.backend.recommendation.DestinationProfiles.Profile;
import com.tripbudgetplanner.backend.recommendation.dto.BudgetRecommendation;
import com.tripbudgetplanner.backend.recommendation.dto.BudgetRecommendationRequest;
import com.tripbudgetplanner.backend.recommendation.dto.TimingRecommendation;
import com.tripbudgetplanner.backend.recommendation.dto.TimingRecommendationRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

// Default recommendations: fixed rules and rough cost tables, no external calls.
// Replace with an AI provider later (see RecommendationProvider).
@Service
public class RuleBasedRecommendationProvider implements RecommendationProvider {

    private static final String SOURCE = "rules";

    // Rough daily costs in USD for one traveler (room is per room, shared by up to 2)
    private record DailyCosts(int roomPerNight, int foodPerPerson, int transportPerPerson, int activitiesPerPerson) {}

    private static final Map<CostLevel, DailyCosts> DAILY_COSTS = Map.of(
        CostLevel.LOW, new DailyCosts(70, 30, 10, 15),
        CostLevel.MEDIUM, new DailyCosts(140, 55, 18, 30),
        CostLevel.HIGH, new DailyCosts(220, 85, 25, 45)
    );

    // Fixed demo exchange rates (1 USD = x). Replace with a rates API if you need accuracy.
    private static final Map<String, BigDecimal> USD_RATES = Map.of(
        "USD", new BigDecimal("1.00"),
        "EUR", new BigDecimal("0.92"),
        "GBP", new BigDecimal("0.79"),
        "MAD", new BigDecimal("10.00")
    );

    private static final BigDecimal BUFFER = new BigDecimal("1.10");   // 10% left unallocated
    private static final BigDecimal LOW_FACTOR = new BigDecimal("0.85");
    private static final BigDecimal HIGH_FACTOR = new BigDecimal("1.30");

    // ---------------- Timing ----------------

    @Override
    public TimingRecommendation recommendTiming(TimingRecommendationRequest request) {
        int nights = request.nights();
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        LocalDate earliest = request.windowStart().isBefore(tomorrow) ? tomorrow : request.windowStart();
        LocalDate latestStart = request.windowEnd().minusDays(nights);

        if (latestStart.isBefore(earliest)) {
            throw new IllegalArgumentException(
                "Your travel window is shorter than " + nights + " night" + (nights == 1 ? "" : "s") + ". Widen it or shorten the trip.");
        }

        Profile profile = DestinationProfiles.find(request.destination());
        boolean cheapest = "cheapest".equals(request.mode());
        Set<Month> preferredMonths = cheapest ? profile.cheapMonths() : profile.bestMonths();

        // First start date in a preferred month (and, for cheapest, on a Tue/Wed departure)
        LocalDate chosen = null;
        for (LocalDate d = earliest; !d.isAfter(latestStart); d = d.plusDays(1)) {
            if (preferredMonths.contains(d.getMonth()) && (!cheapest || isMidweek(d))) {
                chosen = d;
                break;
            }
        }

        String place = displayName(profile, request.destination());
        String reason;

        if (chosen != null) {
            String month = monthName(chosen.getMonth());
            reason = cheapest
                ? month + " is a low-price time for " + place + ": " + profile.cheapNote()
                    + ". Leaving on a " + dayName(chosen.getDayOfWeek()) + " usually means cheaper fares too."
                : month + " is one of the best times to visit " + place + ": " + profile.bestNote() + ".";
        } else {
            // Nothing in the window hits a preferred month: fall back to the earliest sensible date
            chosen = earliest;
            if (cheapest) {
                for (LocalDate d = earliest; !d.isAfter(latestStart); d = d.plusDays(1)) {
                    if (isMidweek(d)) { chosen = d; break; }
                }
            }
            reason = "None of your window falls in " + place + "'s "
                + (cheapest ? "low season" : "best season")
                + ", so we picked the "
                + (cheapest ? "first midweek departure, when fares are usually lowest." : "earliest dates that fit.")
                + " Widening your window may give a better match.";
        }

        return new TimingRecommendation(chosen, chosen.plusDays(nights), reason, SOURCE);
    }

    // ---------------- Budget ----------------

    @Override
    public BudgetRecommendation recommendBudget(BudgetRecommendationRequest request) {
        Profile profile = DestinationProfiles.find(request.destination());
        DailyCosts daily = DAILY_COSTS.get(profile.costLevel());
        List<String> tradeOffs = request.tradeOffs() == null ? List.of() : request.tradeOffs();

        int nights = request.nights();
        int days = nights + 1;
        int travelers = request.travelers();
        int rooms = (travelers + 1) / 2;

        // Season: peak months cost more, low-season months less
        double season = 1.0;
        String seasonNote = null;
        if (request.startDate() != null) {
            Month month = request.startDate().getMonth();
            if (profile.cheapMonths().contains(month)) {
                season = 0.85;
                seasonNote = monthName(month) + " is low season, so flights and rooms are estimated about 15% lower.";
            } else if (profile.bestMonths().contains(month)) {
                season = 1.15;
                seasonNote = monthName(month) + " is a popular time, so flights and rooms are estimated about 15% higher.";
            }
        }

        // Trade-offs the traveler said they're OK with
        double lodgingFactor = 1.0;
        if (tradeOffs.contains("budgetHotels")) lodgingFactor = Math.min(lodgingFactor, 0.75);
        if (tradeOffs.contains("sharedRooms")) lodgingFactor = Math.min(lodgingFactor, 0.55);
        double foodFactor = tradeOffs.contains("lowCostMeals") ? 0.65 : 1.0;
        double flightFactor = tradeOffs.contains("longLayovers") ? 0.85 : 1.0;
        double activitiesFactor = 1.0;
        if (tradeOffs.contains("lowerCostActivities")) activitiesFactor = 0.6;
        if (tradeOffs.contains("skipPaidActivities")) activitiesFactor = 0.1;

        // Estimates in USD
        Map<String, Double> usd = new LinkedHashMap<>();
        usd.put("Flights", profile.flightUsdPerPerson() * travelers * flightFactor * season);
        usd.put("Accommodation", (double) daily.roomPerNight() * rooms * nights * lodgingFactor * season);
        usd.put("Food", (double) daily.foodPerPerson() * travelers * days * foodFactor);
        usd.put("Local transport", (double) daily.transportPerPerson() * travelers * days);
        usd.put("Activities", (double) daily.activitiesPerPerson() * travelers * days * activitiesFactor);

        String currency = request.currency();
        BigDecimal rate = USD_RATES.getOrDefault(currency, BigDecimal.ONE);

        Map<String, BigDecimal> allocations = new LinkedHashMap<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        for (Map.Entry<String, Double> entry : usd.entrySet()) {
            BigDecimal amount = BigDecimal.valueOf(entry.getValue()).multiply(rate)
                .setScale(0, RoundingMode.HALF_UP);
            allocations.put(entry.getKey(), amount);
            subtotal = subtotal.add(amount);
        }

        BigDecimal recommended = roundUpToNearest(subtotal.multiply(BUFFER), 50);
        BigDecimal low = roundToNearest(subtotal.multiply(LOW_FACTOR), 50);
        BigDecimal high = roundUpToNearest(subtotal.multiply(HIGH_FACTOR), 50);

        List<String> assumptions = new ArrayList<>();
        assumptions.add(travelers + " traveler" + (travelers == 1 ? "" : "s") + ", " + nights + " night" + (nights == 1 ? "" : "s")
            + ", " + rooms + " room" + (rooms == 1 ? "" : "s") + ".");
        assumptions.add("Round-trip flights from the US.");
        if (seasonNote != null) assumptions.add(seasonNote);
        if (!DestinationProfiles.isKnown(profile)) {
            assumptions.add("We don't have specific data for this destination yet, so this uses average costs.");
        }
        if (!USD_RATES.containsKey(currency)) {
            assumptions.add("No exchange rate for " + currency + ", so amounts are shown at USD value.");
        } else if (!"USD".equals(currency)) {
            assumptions.add("Converted from USD at a fixed demo rate.");
        }
        assumptions.add("About 10% is left unallocated as a buffer.");

        return new BudgetRecommendation(recommended, low, high, currency, allocations, assumptions, SOURCE);
    }

    // ---------------- Helpers ----------------

    private static boolean isMidweek(LocalDate date) {
        return date.getDayOfWeek() == DayOfWeek.TUESDAY || date.getDayOfWeek() == DayOfWeek.WEDNESDAY;
    }

    private static String displayName(Profile profile, String destination) {
        return profile.displayName() != null ? profile.displayName() : destination.trim();
    }

    private static String monthName(Month month) {
        return month.getDisplayName(TextStyle.FULL, Locale.US);
    }

    private static String dayName(DayOfWeek day) {
        return day.getDisplayName(TextStyle.FULL, Locale.US);
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
