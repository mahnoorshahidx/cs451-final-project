package com.tripbudgetplanner.backend.itinerary;

import com.tripbudgetplanner.backend.ai.AiClientException;
import com.tripbudgetplanner.backend.ai.AnthropicClient;
import com.tripbudgetplanner.backend.itinerary.dto.ItineraryActivityResponse;
import com.tripbudgetplanner.backend.itinerary.dto.ItineraryGenerationRequest;
import com.tripbudgetplanner.backend.trip.Trip;
import com.tripbudgetplanner.backend.trip.TripService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// Generates a day-by-day itinerary with Claude and saves it as normal itinerary activities.
// Because they're regular activities, users can edit or delete them with the existing endpoints.
//
// Not @Transactional on purpose: the AI call can take 10-30 seconds, and we don't want to hold
// a database connection open that long. The trip is loaded first, then the AI is called,
// then the activities are saved.
@Service
public class ItineraryGenerationService {

    private static final Logger log = LoggerFactory.getLogger(ItineraryGenerationService.class);

    // Longer trips only get the first MAX_DAYS planned, to keep the AI reply a reasonable size
    static final int MAX_DAYS = 14;

    // Categories the AI may use; anything else becomes "Activity"
    static final List<String> CATEGORIES = List.of("Sightseeing", "Food", "Activity", "Shopping", "Transport", "Rest");

    private static final Map<String, String> PACE = Map.of(
        "relaxed", "2 to 3 activities per day, including meals, with plenty of free time",
        "balanced", "3 to 4 activities per day, including meals",
        "packed", "5 to 6 activities per day, including meals, from morning to evening"
    );

    private static final int MAX_TOKENS = 8000;
    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    private final AnthropicClient ai;
    private final TripService tripService;
    private final ItineraryActivityRepository activityRepository;

    public ItineraryGenerationService(
            AnthropicClient ai,
            TripService tripService,
            ItineraryActivityRepository activityRepository) {
        this.ai = ai;
        this.tripService = tripService;
        this.activityRepository = activityRepository;
    }

    public List<ItineraryActivityResponse> generate(Long tripId, ItineraryGenerationRequest request, Long userId) {
        // Also checks that the logged-in user owns the trip
        Trip trip = tripService.getTrip(tripId, userId);

        if (!ai.isEnabled()) {
            throw unavailable();
        }

        LocalDate start = trip.getStartDate();
        LocalDate end = trip.getEndDate();
        long tripDays = ChronoUnit.DAYS.between(start, end) + 1;
        LocalDate lastPlannedDay = tripDays > MAX_DAYS ? start.plusDays(MAX_DAYS - 1) : end;

        List<ItineraryActivity> activities;
        try {
            String reply = ai.complete(systemPrompt(), userPrompt(trip, request, lastPlannedDay), MAX_TOKENS, TIMEOUT);
            activities = readActivities(ai.parseJsonObject(reply), trip, start, lastPlannedDay);
        } catch (AiClientException e) {
            log.warn("AI itinerary generation failed for trip {}: {}", tripId, e.getMessage());
            throw unavailable();
        }

        if (activities.isEmpty()) {
            log.warn("AI itinerary for trip {} had no usable activities.", tripId);
            throw unavailable();
        }

        if (Boolean.TRUE.equals(request.replaceExisting())) {
            activityRepository.deleteAll(activityRepository.findByTripIdOrderByActivityDateAscStartTimeAsc(tripId));
        }

        return activityRepository.saveAll(activities).stream()
            .map(ItineraryGenerationService::toResponse)
            .toList();
    }

    // ---------------- Prompt ----------------

    private static String systemPrompt() {
        return """
            You are a travel planner inside a trip budgeting app. Plan a realistic day-by-day itinerary.
            Reply with only a JSON object, no other text:
            {
              "activities": [
                {
                  "date": "YYYY-MM-DD",
                  "startTime": "HH:mm",
                  "endTime": "HH:mm",
                  "title": "short name, under 80 characters",
                  "category": "Sightseeing | Food | Activity | Shopping | Transport | Rest",
                  "location": "place or neighborhood, under 100 characters",
                  "notes": "one or two sentences: what to do there and a rough cost per person, in the given currency"
                }
              ]
            }
            Rules:
            - Use only real, well-known places that exist at the destination.
            - Every date must be within the given range. Order activities by date and time.
            - Times are 24-hour. endTime is after startTime on the same day.
            - Keep each day's food and activity costs within the daily budget per person, if one is given.
            - Match the travel style: budget means free and low-cost options, luxury means upscale ones.
            - Prefer places that are usually rated at or above the minimum rating, if one is given.
            - Respect every food and dietary preference when suggesting where to eat.
            - Respect the special requests. Do not suggest anything they ask to avoid.
            - Keep the first and last day lighter for travel.
            """;
    }

    private static String userPrompt(Trip trip, ItineraryGenerationRequest request, LocalDate lastPlannedDay) {
        // "No restrictions" is how the questionnaire says there are none
        List<String> food = request.foodPreferences() == null ? List.of()
            : request.foodPreferences().stream().filter(f -> !"No restrictions".equalsIgnoreCase(f)).toList();
        List<String> interests = request.interests() == null ? List.of() : request.interests();
        String special = request.specialRequests() == null ? "" : request.specialRequests().trim();
        String currency = request.currency() == null ? "USD" : request.currency();
        String rating = request.minimumRating() == null || "any".equals(request.minimumRating())
            ? "no minimum" : request.minimumRating() + " out of 5 stars";

        return """
            Destination: %s
            Plan these dates: %s to %s
            Currency: %s
            Daily budget per person for food and activities: %s
            Travel style: %s
            Minimum place rating: %s
            Interests: %s
            Pace: %s
            Food and dietary preferences: %s
            Special requests: %s
            """.formatted(
                trip.getDestination(),
                trip.getStartDate(), lastPlannedDay,
                currency,
                request.dailyBudget() == null ? "not given" : request.dailyBudget().stripTrailingZeros().toPlainString() + " " + currency,
                request.travelStyle() == null ? "balanced" : request.travelStyle(),
                rating,
                interests.isEmpty() ? "none given" : String.join(", ", interests),
                PACE.get(request.pace()),
                food.isEmpty() ? "no restrictions" : String.join(", ", food),
                special.isEmpty() ? "none" : special);
    }

    // ---------------- Reading the AI reply ----------------

    // Keeps the activities that pass the same rules as manually added ones; skips the rest
    static List<ItineraryActivity> readActivities(JsonNode json, Trip trip, LocalDate first, LocalDate last) {
        JsonNode items = json.path("activities");
        List<ItineraryActivity> result = new ArrayList<>();

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
            result.add(activity);
        }
        return result;
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

    private static ItineraryActivityResponse toResponse(ItineraryActivity activity) {
        return new ItineraryActivityResponse(
            activity.getId(),
            activity.getTrip().getId(),
            activity.getActivityDate(),
            activity.getTitle(),
            activity.getStartTime(),
            activity.getEndTime(),
            activity.getCategory(),
            activity.getLocation(),
            activity.getNotes()
        );
    }
}