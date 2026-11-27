package com.tripbudgetplanner.backend.itinerary;

import com.tripbudgetplanner.backend.ai.AiClientException;
import com.tripbudgetplanner.backend.ai.AnthropicClient;
import com.tripbudgetplanner.backend.currency.SupportedCurrency;
import com.tripbudgetplanner.backend.itinerary.dto.ItineraryActivityResponse;
import com.tripbudgetplanner.backend.itinerary.dto.ItineraryGenerationRequest;
import com.tripbudgetplanner.backend.trip.Trip;
import com.tripbudgetplanner.backend.trip.TripAccessService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Generates a day-by-day itinerary with Claude and saves it as normal itinerary activities.
// Because they're regular activities, users can edit or delete them with the existing endpoints.
//
// The user's questionnaire answers are applied in two places:
//   1. the prompt asks for them (budget, style, rating, interests, pace, food, special requests);
//   2. the reply is checked: dates must be in range, each day keeps at most the pace's number of
//      activities, and each activity gets a cost (per person from the AI, times the travelers)
//      in the trip's currency, so the app can show it against the daily budget.
//
// Not @Transactional on purpose: the AI call can take 10-30 seconds, and we don't want to hold
// a database connection open that long. The trip is loaded first, then the AI is called,
// then the activities are saved.
@Service
public class ItineraryGenerationService {

    private static final Logger log = LoggerFactory.getLogger(ItineraryGenerationService.class);

    // At most this many days are planned per request, to keep the AI reply a reasonable size.
    // Longer trips are planned in parts by sending startDate/endDate.
    static final int MAX_DAYS = 14;

    // Categories the AI may use; anything else becomes "Activity"
    static final List<String> CATEGORIES = List.of("Sightseeing", "Food", "Activity", "Shopping", "Transport", "Rest");

    private static final Map<String, String> PACE = Map.of(
        "relaxed", "2 to 3 activities per day, including meals, with plenty of free time",
        "balanced", "3 to 4 activities per day, including meals",
        "packed", "5 to 6 activities per day, including meals, from morning to evening"
    );

    // Most activities kept per day for each pace (extra ones from the AI are dropped)
    static final Map<String, Integer> PACE_LIMIT = Map.of("relaxed", 3, "balanced", 4, "packed", 6);

    private static final int MAX_TOKENS = 16000;
    private static final Duration TIMEOUT = Duration.ofSeconds(120);

    private final AnthropicClient ai;
    private final TripAccessService tripAccess;
    private final ItineraryActivityRepository activityRepository;

    public ItineraryGenerationService(
            AnthropicClient ai,
            TripAccessService tripAccess,
            ItineraryActivityRepository activityRepository) {
        this.ai = ai;
        this.tripAccess = tripAccess;
        this.activityRepository = activityRepository;
    }

    public List<ItineraryActivityResponse> generate(Long tripId, ItineraryGenerationRequest request, Long userId) {
        // Owner or editor can add AI activities (403 for viewers, 404 without access)
        Trip trip = tripAccess.editableTrip(tripId, userId);
        checkPlannable(trip);
        PlanRange range = planRange(trip, request);

        List<ItineraryActivity> activities;
        try {
            activities = plan(trip, request, range).activities();
        } catch (AiClientException e) {
            log.warn("AI itinerary generation failed for trip {}: {}", tripId, e.getMessage());
            throw unavailable();
        }

        if (activities.isEmpty()) {
            log.warn("AI itinerary for trip {} had no usable activities.", tripId);
            throw unavailable();
        }

        if (Boolean.TRUE.equals(request.replaceExisting())) {
            clearForReplace(tripId, range);
        }

        return activityRepository.saveAll(activities).stream()
            .map(ItineraryActivityService::toResponse)
            .toList();
    }

    // ---------------- Planning without saving (also used for UC5 trip options) ----------------

    // Which days a request plans: inside the trip, at most MAX_DAYS
    public record PlanRange(LocalDate first, LocalDate last, boolean hasRange) {}

    // One AI plan: unsaved activities and the AI's one-line description of it
    public record Plan(List<ItineraryActivity> activities, String summary) {}

    // 400 for an ended trip, 503 when the AI isn't configured
    public void checkPlannable(Trip trip) {
        if (trip.getEndDate().isBefore(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "This trip has already ended, so there's nothing left to plan.");
        }
        if (!ai.isEnabled()) {
            throw unavailable();
        }
    }

    // The requested range, or from the trip's first day; 400 outside the trip. At most MAX_DAYS.
    public PlanRange planRange(Trip trip, ItineraryGenerationRequest request) {
        boolean hasRange = request.startDate() != null || request.endDate() != null;
        LocalDate first = request.startDate() != null ? request.startDate() : trip.getStartDate();
        LocalDate last = request.endDate() != null ? request.endDate() : trip.getEndDate();
        if (first.isBefore(trip.getStartDate()) || last.isAfter(trip.getEndDate()) || last.isBefore(first)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Days to plan must be within the trip dates, and the end can't be before the start.");
        }
        if (ChronoUnit.DAYS.between(first, last) + 1 > MAX_DAYS) {
            last = first.plusDays(MAX_DAYS - 1);
        }
        return new PlanRange(first, last, hasRange);
    }

    // Asks the AI for one itinerary over the range. Nothing is saved.
    public Plan plan(Trip trip, ItineraryGenerationRequest request, PlanRange range) {
        String prompt = userPrompt(trip, request, range.first(), range.last()) + bookedLine(trip, range);
        String reply = ai.complete(systemPrompt(), prompt, MAX_TOKENS, TIMEOUT);
        JsonNode json = ai.parseJsonObject(reply);
        List<ItineraryActivity> activities = readActivities(json, trip, range.first(), range.last(),
            PACE_LIMIT.get(request.pace()), Math.max(1, trip.getNumberOfTravelers()), trip.getCurrency());
        return new Plan(activities, clean(text(json, "summary"), 300));
    }

    // Flights, hotels and other items added through travel search on these days, so the AI plans around them
    private String bookedLine(Trip trip, PlanRange range) {
        if (trip.getId() == null) return "";
        List<String> booked = activityRepository
            .findByTripIdAndActivityDateBetween(trip.getId(), range.first(), range.last()).stream()
            .filter(a -> a.getSourceType() != null)
            .map(a -> a.getActivityDate() + (a.getStartTime() != null ? " " + a.getStartTime() : "") + " " + a.getTitle())
            .limit(40)
            .toList();
        return booked.isEmpty() ? ""
            : "Already booked on these days (plan around them, don't add them again): " + String.join("; ", booked) + "\n";
    }

    // Before replacing days: delete the AI and manual activities there, but keep flights, hotels and
    // other items added through travel search (UC6). Their cost is in the budget, and the user
    // booked them on purpose.
    public void clearForReplace(Long tripId, PlanRange range) {
        List<ItineraryActivity> existing = range.hasRange()
            ? activityRepository.findByTripIdAndActivityDateBetween(tripId, range.first(), range.last())
            : activityRepository.findByTripIdOrderByActivityDateAscStartTimeAsc(tripId);
        activityRepository.deleteAll(existing.stream().filter(a -> a.getSourceType() == null).toList());
    }

    // ---------------- Prompt ----------------

    private static String systemPrompt() {
        return """
            You are a travel planner inside a trip budgeting app. Plan a realistic day-by-day itinerary.
            Reply with only a JSON object, no other text:
            {
              "summary": "one sentence on what this plan is like, under 200 characters",
              "activities": [
                {
                  "date": "YYYY-MM-DD",
                  "startTime": "HH:mm",
                  "endTime": "HH:mm",
                  "title": "short name, under 80 characters",
                  "category": "Sightseeing | Food | Activity | Shopping | Transport | Rest",
                  "location": "place or neighborhood, under 100 characters",
                  "costPerPerson": 25,
                  "notes": "one or two sentences: what to do there and why it fits the traveler"
                }
              ]
            }
            Rules:
            - Use only real, well-known places that exist at the destination.
            - Plan every date in the given range, and only those dates. Order activities by date and time.
            - Times are 24-hour. endTime is after startTime on the same day. Activities on a day don't overlap.
            - costPerPerson is a plain number: a realistic estimate in the given currency, 0 for free things.
            - Each day's total costPerPerson must stay within the daily budget per person, if one is given.
            - Match the travel style: budget means free and low-cost options, luxury means upscale ones.
            - Prefer places that are usually rated at or above the minimum rating, if one is given.
            - Most activities that aren't meals must match the traveler's interests. Spread the interests across the days.
            - Follow the pace exactly: never plan more activities in a day than it allows.
            - Every meal must respect every food and dietary preference.
            - Respect the special requests. Do not suggest anything they ask to avoid.
            - Never repeat a place from the "already planned" list or from another day.
            - Keep the trip's first and last day lighter for travel.
            """;
    }

    static String userPrompt(Trip trip, ItineraryGenerationRequest request, LocalDate first, LocalDate last) {
        // "No restrictions" is how the questionnaire says there are none
        List<String> food = request.foodPreferences() == null ? List.of()
            : request.foodPreferences().stream().filter(f -> !"No restrictions".equalsIgnoreCase(f)).toList();
        List<String> interests = request.interests() == null ? List.of() : request.interests();
        List<String> avoid = request.avoidTitles() == null ? List.of() : request.avoidTitles();
        String special = request.specialRequests() == null ? "" : request.specialRequests().trim();
        String currency = trip.getCurrency();
        String rating = request.minimumRating() == null || "any".equals(request.minimumRating())
            ? "no minimum" : request.minimumRating() + " out of 5 stars";
        String pace = PACE.getOrDefault(request.pace(), PACE.get("balanced"));

        return """
            Destination: %s
            Trip dates: %s to %s (the first and last day are travel days)
            Plan these dates: %s to %s
            Travelers: %d
            Currency: %s
            Daily budget per person for food and activities: %s
            Travel style: %s
            Minimum place rating: %s
            Interests: %s
            Pace: %s
            Food and dietary preferences: %s
            Special requests: %s
            Already planned on other days (do not repeat): %s
            """.formatted(
                trip.getDestination(),
                trip.getStartDate(), trip.getEndDate(),
                first, last,
                Math.max(1, trip.getNumberOfTravelers()),
                currency,
                request.dailyBudget() == null ? "not given" : request.dailyBudget().stripTrailingZeros().toPlainString() + " " + currency,
                request.travelStyle() == null ? "balanced" : request.travelStyle(),
                rating,
                interests.isEmpty() ? "none given" : String.join(", ", interests),
                pace,
                food.isEmpty() ? "no restrictions" : String.join(", ", food),
                special.isEmpty() ? "none" : special,
                avoid.isEmpty() ? "nothing yet" : String.join("; ", avoid));
    }

    // ---------------- Reading the AI reply ----------------

    // Keeps the activities that pass the same rules as manually added ones; skips the rest.
    // (No pace limit and no costs: the original form, used by older tests.)
    static List<ItineraryActivity> readActivities(JsonNode json, Trip trip, LocalDate first, LocalDate last) {
        return readActivities(json, trip, first, last, null, 1, null);
    }

    // perDayLimit: most activities kept per day (null = no limit), earliest first.
    // travelers and currency turn the AI's costPerPerson into the activity's group cost
    // (no cost is saved when the currency is missing or unsupported).
    static List<ItineraryActivity> readActivities(JsonNode json, Trip trip, LocalDate first, LocalDate last,
                                                  Integer perDayLimit, int travelers, String currency) {
        JsonNode items = json.path("activities");
        List<ItineraryActivity> result = new ArrayList<>();
        SupportedCurrency costCurrency = currency != null && SupportedCurrency.isSupported(currency)
            ? SupportedCurrency.fromCode(currency) : null;

        for (int i = 0; i < items.size() && result.size() < MAX_DAYS * 8; i++) {
            JsonNode item = items.get(i);

            LocalDate date;
            try {
                date = LocalDate.parse(text(item, "date"));
            } catch (DateTimeParseException e) {
                continue;
            }
            if (date.isBefore(first) || date.isAfter(last)) {
                continue;
            }

            String title = clean(text(item, "title"), 255);
            if (title.isEmpty()) {
                continue;
            }

            LocalTime startTime = parseTime(text(item, "startTime"));
            LocalTime endTime = parseTime(text(item, "endTime"));
            // Same rules as ItineraryActivityService: no end without a start, end after start
            if (startTime == null || (endTime != null && !endTime.isAfter(startTime))) {
                endTime = null;
            }

            String category = clean(text(item, "category"), 100);
            if (!CATEGORIES.contains(category)) {
                category = "Activity";
            }

            String location = clean(text(item, "location"), 255);
            String notes = clean(text(item, "notes"), 1000);

            ItineraryActivity activity = new ItineraryActivity();
            activity.setTrip(trip);
            activity.setActivityDate(date);
            activity.setTitle(title);
            activity.setStartTime(startTime);
            activity.setEndTime(endTime);
            activity.setCategory(category);
            activity.setLocation(location.isEmpty() ? null : location);
            activity.setNotes((notes.isEmpty() ? "" : notes + " ") + "(AI suggestion)");

            BigDecimal perPerson = number(item.path("costPerPerson"));
            if (costCurrency != null && perPerson != null) {
                BigDecimal groupCost = perPerson.multiply(BigDecimal.valueOf(Math.max(1, travelers)))
                    .setScale(costCurrency.getDecimalPlaces(), RoundingMode.HALF_UP);
                activity.setEstimatedCost(groupCost, costCurrency.name());
            }

            result.add(activity);
        }

        return perDayLimit == null ? result : limitPerDay(result, perDayLimit);
    }

    // Keeps the earliest `limit` activities on each day (activities without a time count as last)
    static List<ItineraryActivity> limitPerDay(List<ItineraryActivity> activities, int limit) {
        List<ItineraryActivity> sorted = new ArrayList<>(activities);
        sorted.sort(Comparator.comparing(ItineraryActivity::getActivityDate)
            .thenComparing(ItineraryActivity::getStartTime, Comparator.nullsLast(Comparator.naturalOrder())));

        Map<LocalDate, Integer> perDay = new HashMap<>();
        List<ItineraryActivity> kept = new ArrayList<>();
        for (ItineraryActivity activity : sorted) {
            int count = perDay.merge(activity.getActivityDate(), 1, Integer::sum);
            if (count <= limit) {
                kept.add(activity);
            }
        }
        return kept;
    }

    // A non-negative number from a JSON number or numeric text; null otherwise
    private static BigDecimal number(JsonNode node) {
        BigDecimal value = null;
        if (node.isNumber()) {
            value = node.decimalValue();
        } else if (node.isString()) {
            try {
                value = new BigDecimal(node.asString().replaceAll("[^0-9.]", ""));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return value == null || value.signum() < 0 || value.compareTo(BigDecimal.valueOf(1_000_000)) > 0
            ? null : value;
    }

    // Field as text; "" when missing or null (asString() would give "null" for a JSON null)
    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? "" : value.asString();
    }

    private static LocalTime parseTime(String text) {
        try {
            return text == null || text.isBlank() ? null : LocalTime.parse(text.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String clean(String text, int maxLength) {
        String clean = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        return clean.length() <= maxLength ? clean : clean.substring(0, maxLength - 1).trim() + "…";
    }

    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
            "AI itinerary suggestions aren't available right now. Try again in a moment, or add activities yourself.");
    }
}
