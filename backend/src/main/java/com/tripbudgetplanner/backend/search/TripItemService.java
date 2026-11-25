package com.tripbudgetplanner.backend.search;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tripbudgetplanner.backend.budget.TripBudgetRepository;
import com.tripbudgetplanner.backend.budget.TripBudgetService;
import com.tripbudgetplanner.backend.budget.dto.BudgetChange;
import com.tripbudgetplanner.backend.currency.CurrencyService;
import com.tripbudgetplanner.backend.currency.ExchangeRates;
import com.tripbudgetplanner.backend.currency.SupportedCurrency;
import com.tripbudgetplanner.backend.itinerary.ItineraryActivity;
import com.tripbudgetplanner.backend.itinerary.ItineraryActivityRepository;
import com.tripbudgetplanner.backend.itinerary.ItineraryActivityService;
import com.tripbudgetplanner.backend.search.dto.AddTripItemRequest;
import com.tripbudgetplanner.backend.search.dto.AddTripItemResponse;
import com.tripbudgetplanner.backend.search.dto.SearchResult;
import com.tripbudgetplanner.backend.search.exception.DuplicateTripItemException;
import com.tripbudgetplanner.backend.search.exception.SearchItemMismatchException;
import com.tripbudgetplanner.backend.search.exception.SearchItemNotFoundException;
import com.tripbudgetplanner.backend.trip.Trip;
import com.tripbudgetplanner.backend.trip.TripAccessService;

// UC6: adds a search result to the trip's itinerary, and optionally to the budget.
//
// Everything happens in one transaction while the trip row is locked, so:
//  - two members adding the same item at once can't create duplicates
//  - budget changes from different members can't overwrite each other
//  - the trip's currency can't change halfway through
// If any step fails (duplicate, unsupported budget currency, ...), nothing is saved.
//
// Note: deleting the activity later, or replacing the itinerary with the AI generator,
// does NOT take the amount back out of the budget. budgetAmountAdded shows what was added.
@Service
public class TripItemService {

    static final String MAIN = "MAIN";
    static final String OUTBOUND = "OUTBOUND";
    static final String RETURN = "RETURN";

    private final TravelSearchProvider provider;
    private final TravelSearchService searchService;
    private final TripAccessService tripAccess;
    private final ItineraryActivityRepository activityRepository;
    private final TripBudgetRepository budgetRepository;
    private final TripBudgetService budgetService;
    private final CurrencyService currencyService;

    public TripItemService(TravelSearchProvider provider, TravelSearchService searchService,
                           TripAccessService tripAccess, ItineraryActivityRepository activityRepository,
                           TripBudgetRepository budgetRepository, TripBudgetService budgetService,
                           CurrencyService currencyService) {
        this.provider = provider;
        this.searchService = searchService;
        this.tripAccess = tripAccess;
        this.activityRepository = activityRepository;
        this.budgetRepository = budgetRepository;
        this.budgetService = budgetService;
        this.currencyService = currencyService;
    }

    @Transactional
    public AddTripItemResponse addItem(Long tripId, AddTripItemRequest request, Long userId) {
        // Owner or editor only (403 for viewers, 404 without access, 401 when signed out)
        Trip trip = tripAccess.editableTrip(tripId, userId);

        // Lock the trip so concurrent adds, budget edits and currency changes wait their turn
        budgetRepository.lockTrip(tripId);
        SupportedCurrency currency = SupportedCurrency.fromCode(
            budgetRepository.findTripCurrency(tripId).orElse(trip.getCurrency()));

        // Look the item up again: the price and details come from the provider, not the request
        String itemId = request.itemId().trim();
        ProviderItem item = provider.findItem(itemId)
            .orElseThrow(() -> new SearchItemNotFoundException(
                "This search result isn't available anymore. Search again."));
        checkMatchesTrip(item, trip);

        if (activityRepository.existsByTripIdAndSourceProviderAndSourceItemId(tripId, provider.name(), itemId)) {
            throw new DuplicateTripItemException("This item is already in your itinerary.");
        }

        LocalTime startOverride = checkStartTime(item, request.startTime());

        // One exchange-rate snapshot for pricing the item and the budget update
        ExchangeRates rates = item.priceCurrency() == currency ? null : currencyService.currentRates();
        SearchResult priced = searchService.price(item, currency, rates, false).result();

        List<ItineraryActivity> activities = buildActivities(trip, item, priced, currency, startOverride,
            clean(request.notes()));

        // The cost (and any budget amount) is stored once, on the first entry
        ItineraryActivity costEntry = activities.get(0);
        BudgetChange budgetChange = null;
        if (Boolean.TRUE.equals(request.updateBudget())) {
            budgetChange = budgetService.addToCategory(tripId, item.type().getBudgetCategory(),
                priced.estimatedTotal(), currency, rates, userId);
            costEntry.setBudgetAmountAdded(budgetChange.amountAdded(), budgetChange.currency());
        }

        List<ItineraryActivity> saved = new ArrayList<>();
        try {
            for (ItineraryActivity activity : activities) {
                saved.add(activityRepository.saveAndFlush(activity));
            }
        } catch (DataIntegrityViolationException e) {
            // The unique key caught a duplicate; the whole request is rolled back
            throw new DuplicateTripItemException("This item is already in your itinerary.");
        }

        return new AddTripItemResponse(
            saved.stream().map(ItineraryActivityService::toResponse).toList(),
            priced.withAlreadyAdded(),
            budgetChange,
            provider.live() ? null : TravelSearchService.DEMO_NOTICE);
    }

    // ---------- Checks ----------

    // The item must come from a search for this trip: same destination, group size and dates.
    // This rejects IDs edited by hand, and results from before the trip was changed.
    private void checkMatchesTrip(ProviderItem item, Trip trip) {
        if (!item.destinationKey().equals(provider.destinationKey(trip.getDestination()))) {
            throw mismatch("it is for a different destination");
        }
        if (item.travelers() != Math.max(1, trip.getNumberOfTravelers())) {
            throw mismatch("it was priced for " + item.travelers() + " travelers, but the trip has "
                + trip.getNumberOfTravelers());
        }
        boolean startOk = !item.date().isBefore(trip.getStartDate()) && !item.date().isAfter(trip.getEndDate());
        boolean endOk = item.endDate() == null
            || (!item.endDate().isBefore(trip.getStartDate()) && !item.endDate().isAfter(trip.getEndDate()));
        if (!startOk || !endOk) {
            throw mismatch("its dates are outside the trip dates");
        }
    }

    private static SearchItemMismatchException mismatch(String reason) {
        return new SearchItemMismatchException("This search result doesn't match your trip: " + reason
            + ". Search again.");
    }

    // A new start time is only allowed for restaurants and activities (flight and hotel times are fixed)
    private static LocalTime checkStartTime(ProviderItem item, LocalTime startTime) {
        if (startTime == null) {
            return null;
        }
        if (item.type() == TripItemType.FLIGHT || item.type() == TripItemType.HOTEL) {
            throw new IllegalArgumentException("startTime can only be changed for restaurants and activities");
        }
        Duration length = Duration.between(item.startTime(), item.endTime());
        if (startTime.plus(length).isBefore(startTime)) {
            throw new IllegalArgumentException("That start time would end after midnight");
        }
        return startTime;
    }

    // ---------- Building the itinerary entries ----------

    private List<ItineraryActivity> buildActivities(Trip trip, ProviderItem item, SearchResult priced,
                                                    SupportedCurrency currency, LocalTime startOverride,
                                                    String userNotes) {
        String priceNote = "Estimated " + priced.estimatedTotal().toPlainString() + " " + currency
            + " (" + priced.unitPrice().toPlainString() + " " + priced.priceBasisLabel()
            + ", " + item.quantityDescription() + ")" + (provider.live() ? "." : ". Demo data.");

        List<ItineraryActivity> result = new ArrayList<>();
        switch (item.type()) {
            case FLIGHT -> {
                String flights = item.details().get("from") + " → " + item.details().get("to");
                String back = item.details().get("to") + " → " + item.details().get("from");
                ItineraryActivity out = entry(trip, item, OUTBOUND,
                    "Flight " + flights + " (" + item.details().get("outboundFlight") + ")", flights,
                    item.date(), item.startTime(), item.endTime(),
                    join(item.details().get("airline") + ", " + stops(item) + ". Round trip. " + priceNote
                        + (item.endTime() == null ? " Arrives the next day." : ""), userNotes));
                out.setEstimatedCost(priced.estimatedTotal(), currency.name());

                ItineraryActivity ret = entry(trip, item, RETURN,
                    "Return flight " + back + " (" + item.details().get("returnFlight") + ")", back,
                    item.endDate(), item.returnStartTime(), item.returnEndTime(),
                    join("Price included with the outbound flight on " + item.date() + "."
                        + (item.returnEndTime() == null ? " Arrives the next day." : ""), userNotes));
                result.add(out);
                result.add(ret);
            }
            case HOTEL -> {
                ItineraryActivity stay = entry(trip, item, MAIN, "Check in: " + item.name(), item.location(),
                    item.date(), item.startTime(), null,
                    join(item.quantityDescription() + ", check out " + item.endDate() + ". " + priceNote, userNotes));
                stay.setEstimatedCost(priced.estimatedTotal(), currency.name());
                result.add(stay);
            }
            case RESTAURANT, ACTIVITY -> {
                LocalTime start = startOverride != null ? startOverride : item.startTime();
                LocalTime end = start.plus(Duration.between(item.startTime(), item.endTime()));
                ItineraryActivity visit = entry(trip, item, MAIN, item.name(), item.location(),
                    item.date(), start, end, join(priceNote, userNotes));
                visit.setEstimatedCost(priced.estimatedTotal(), currency.name());
                result.add(visit);
            }
        }
        return result;
    }

    private ItineraryActivity entry(Trip trip, ProviderItem item, String segment, String title, String location,
                                    LocalDate date, LocalTime start, LocalTime end, String notes) {
        ItineraryActivity activity = new ItineraryActivity();
        activity.setTrip(trip);
        activity.setActivityDate(date);
        activity.setTitle(limit(title, 255));
        activity.setStartTime(start);
        activity.setEndTime(end);
        activity.setCategory(item.type().getItineraryCategory());
        activity.setLocation(limit(location, 255));
        activity.setNotes(notes);
        activity.setSource(item.type().name(), provider.name(), item.itemId(), segment);
        return activity;
    }

    private static String stops(ProviderItem item) {
        return "0".equals(item.details().get("stops")) ? "nonstop" : item.details().get("stops") + " stop";
    }

    private static String join(String generated, String userNotes) {
        return userNotes == null ? generated : generated + "\n" + userNotes;
    }

    private static String clean(String notes) {
        return notes == null || notes.isBlank() ? null : notes.trim();
    }

    private static String limit(String text, int max) {
        return text == null || text.length() <= max ? text : text.substring(0, max);
    }
}
