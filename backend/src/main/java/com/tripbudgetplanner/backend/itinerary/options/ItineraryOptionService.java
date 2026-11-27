package com.tripbudgetplanner.backend.itinerary.options;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.tripbudgetplanner.backend.itinerary.ItineraryActivity;
import com.tripbudgetplanner.backend.itinerary.ItineraryActivityRepository;
import com.tripbudgetplanner.backend.itinerary.ItineraryActivityService;
import com.tripbudgetplanner.backend.itinerary.ItineraryGenerationService;
import com.tripbudgetplanner.backend.itinerary.ItineraryGenerationService.Plan;
import com.tripbudgetplanner.backend.itinerary.ItineraryGenerationService.PlanRange;
import com.tripbudgetplanner.backend.itinerary.dto.ItineraryActivityResponse;
import com.tripbudgetplanner.backend.itinerary.dto.ItineraryGenerationRequest;
import com.tripbudgetplanner.backend.trip.Trip;
import com.tripbudgetplanner.backend.trip.TripAccessService;

// UC5: the AI makes a few itinerary options from the same preferences (budget, balanced, luxury),
// the user compares their costs and days, and applies one. Options are drafts until applied:
// nothing changes in the real itinerary before that.
//
// The options are planned in parallel, so it takes about as long as one itinerary.
// Generating again replaces the trip's previous drafts.
@Service
public class ItineraryOptionService {

    private static final Logger log = LoggerFactory.getLogger(ItineraryOptionService.class);

    record Style(String key, String label) {}

    static final List<Style> STYLES = List.of(
        new Style("budget", "Budget-friendly"),
        new Style("balanced", "Balanced"),
        new Style("luxury", "Treat yourself"));

    private final ItineraryGenerationService generator;
    private final TripAccessService tripAccess;
    private final ItineraryOptionRepository options;
    private final ItineraryActivityRepository activities;

    public ItineraryOptionService(ItineraryGenerationService generator, TripAccessService tripAccess,
                                  ItineraryOptionRepository options, ItineraryActivityRepository activities) {
        this.generator = generator;
        this.tripAccess = tripAccess;
        this.options = options;
        this.activities = activities;
    }

    // Plans one option per style and saves them as drafts (owner or editor).
    // Options that fail are left out; 503 only when none worked.
    public List<ItineraryOptionResponse> generate(Long tripId, ItineraryGenerationRequest request, Long userId) {
        Trip trip = tripAccess.editableTrip(tripId, userId);
        generator.checkPlannable(trip);
        PlanRange range = generator.planRange(trip, request);

        List<ItineraryOption> made = new ArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(STYLES.size());
        try {
            List<Future<Plan>> plans = new ArrayList<>();
            for (Style style : STYLES) {
                ItineraryGenerationRequest styled = withStyle(request, style.key());
                plans.add(pool.submit(() -> generator.plan(trip, styled, range)));
            }
            for (int i = 0; i < STYLES.size(); i++) {
                try {
                    Plan plan = plans.get(i).get();
                    if (!plan.activities().isEmpty()) {
                        made.add(toOption(trip, STYLES.get(i), plan, range, userId));
                    }
                } catch (ExecutionException e) {
                    log.warn("AI option '{}' failed for trip {}: {}", STYLES.get(i).key(), tripId,
                        e.getCause() == null ? e.getMessage() : e.getCause().getMessage());
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } finally {
            pool.shutdownNow();
        }

        if (made.isEmpty()) {
            throw unavailable();
        }

        // Only the newest set of options is kept
        options.deleteAll(options.findByTripIdOrderByIdAsc(tripId));
        return options.saveAll(made).stream().map(ItineraryOptionResponse::of).toList();
    }

    // The trip's current drafts (empty when there are none)
    @Transactional(readOnly = true)
    public List<ItineraryOptionResponse> list(Long tripId, Long userId) {
        tripAccess.editableTrip(tripId, userId);
        return options.findByTripIdOrderByIdAsc(tripId).stream().map(ItineraryOptionResponse::of).toList();
    }

    // Copies the chosen option into the itinerary and clears the drafts.
    // replaceExisting: first remove the AI and manual activities on the option's days
    // (items added through travel search always stay).
    @Transactional
    public List<ItineraryActivityResponse> apply(Long tripId, Long optionId, boolean replaceExisting, Long userId) {
        Trip trip = tripAccess.editableTrip(tripId, userId);
        ItineraryOption option = options.findByIdAndTripId(optionId, tripId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "That option is no longer available. Generate new options."));

        if (replaceExisting) {
            generator.clearForReplace(tripId, new PlanRange(option.getStartDate(), option.getEndDate(), true));
        }

        List<ItineraryActivity> created = option.getActivities().stream()
            .filter(a -> !a.getActivityDate().isBefore(trip.getStartDate()) && !a.getActivityDate().isAfter(trip.getEndDate()))
            .map(a -> toActivity(trip, a))
            .toList();

        List<ItineraryActivityResponse> saved = activities.saveAll(created).stream()
            .map(ItineraryActivityService::toResponse)
            .toList();
        options.deleteAll(options.findByTripIdOrderByIdAsc(tripId));
        return saved;
    }

    // Throws away the drafts without changing the itinerary
    @Transactional
    public void discard(Long tripId, Long userId) {
        tripAccess.editableTrip(tripId, userId);
        options.deleteAll(options.findByTripIdOrderByIdAsc(tripId));
    }

    // ---------------- helpers ----------------

    static ItineraryGenerationRequest withStyle(ItineraryGenerationRequest r, String travelStyle) {
        return new ItineraryGenerationRequest(r.dailyBudget(), r.currency(), travelStyle, r.minimumRating(),
            r.interests(), r.pace(), r.foodPreferences(), r.specialRequests(), r.replaceExisting(),
            r.startDate(), r.endDate(), r.avoidTitles());
    }

    static ItineraryOption toOption(Trip trip, Style style, Plan plan, PlanRange range, Long userId) {
        ItineraryOption option = new ItineraryOption();
        option.setTrip(trip);
        option.setStyle(style.key());
        option.setLabel(style.label());
        option.setSummary(plan.summary() == null || plan.summary().isBlank() ? null : plan.summary());
        option.setStartDate(range.first());
        option.setEndDate(range.last());
        option.setCreatedByUserId(userId);
        option.setCreatedAt(Instant.now());

        BigDecimal total = null;
        String currency = null;
        for (ItineraryActivity a : plan.activities()) {
            ItineraryOptionActivity copy = new ItineraryOptionActivity();
            copy.setActivityDate(a.getActivityDate());
            copy.setTitle(a.getTitle());
            copy.setStartTime(a.getStartTime());
            copy.setEndTime(a.getEndTime());
            copy.setCategory(a.getCategory());
            copy.setLocation(a.getLocation());
            copy.setNotes(a.getNotes());
            if (a.getEstimatedCost() != null) {
                copy.setEstimatedCost(a.getEstimatedCost(), a.getCostCurrency());
                total = total == null ? a.getEstimatedCost() : total.add(a.getEstimatedCost());
                if (currency == null) currency = a.getCostCurrency();
            }
            option.addActivity(copy);
        }
        option.setTotalCost(total);
        option.setCurrency(currency);
        return option;
    }

    private static ItineraryActivity toActivity(Trip trip, ItineraryOptionActivity a) {
        ItineraryActivity activity = new ItineraryActivity();
        activity.setTrip(trip);
        activity.setActivityDate(a.getActivityDate());
        activity.setTitle(a.getTitle());
        activity.setStartTime(a.getStartTime());
        activity.setEndTime(a.getEndTime());
        activity.setCategory(a.getCategory());
        activity.setLocation(a.getLocation());
        activity.setNotes(a.getNotes());
        if (a.getEstimatedCost() != null) {
            activity.setEstimatedCost(a.getEstimatedCost(), a.getCostCurrency());
        }
        return activity;
    }

    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
            "AI suggestions aren't available right now. Please try again in a moment.");
    }
}
