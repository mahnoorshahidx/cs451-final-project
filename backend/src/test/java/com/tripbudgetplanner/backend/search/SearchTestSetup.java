package com.tripbudgetplanner.backend.search;

import static com.tripbudgetplanner.backend.member.SharedTripFixture.TRIP_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.tripbudgetplanner.backend.budget.TripBudget;
import com.tripbudgetplanner.backend.budget.TripBudgetRepository;
import com.tripbudgetplanner.backend.budget.TripBudgetService;
import com.tripbudgetplanner.backend.currency.CurrencyService;
import com.tripbudgetplanner.backend.currency.FixedExchangeRateProvider;
import com.tripbudgetplanner.backend.itinerary.ItineraryActivity;
import com.tripbudgetplanner.backend.itinerary.ItineraryActivityRepository;
import com.tripbudgetplanner.backend.member.SharedTripFixture;

// Test setup for UC6: the shared Tokyo trip (3 travelers, USD, 2027-06-01 to 06-10),
// the real demo provider and services, fixed demo exchange rates (1 USD = 150 JPY = 0.92 EUR),
// and repositories that keep what was saved in simple lists.
class SearchTestSetup {

    final SharedTripFixture f = new SharedTripFixture();
    final MockTravelSearchProvider provider = new MockTravelSearchProvider();
    final CurrencyService currency =
        new CurrencyService(new FixedExchangeRateProvider(), new FixedExchangeRateProvider(), "fixed", 60);

    final ItineraryActivityRepository activityRepository = mock(ItineraryActivityRepository.class);
    final TripBudgetRepository budgetRepository = mock(TripBudgetRepository.class);

    final List<ItineraryActivity> savedActivities = new ArrayList<>();
    TripBudget budget;   // null until one is saved
    int budgetSaves = 0;
    int locks = 0;

    final TripBudgetService budgetService;
    final TravelSearchService searchService;
    final TripItemService itemService;

    SearchTestSetup() {
        budgetService = new TripBudgetService(budgetRepository, f.access, currency);
        searchService = new TravelSearchService(provider, f.access, currency, activityRepository);
        itemService = new TripItemService(provider, searchService, f.access, activityRepository,
            budgetRepository, budgetService, currency);

        when(activityRepository.saveAndFlush(any())).thenAnswer(call -> {
            ItineraryActivity a = call.getArgument(0);
            if (a != null) {
                savedActivities.add(a);
            }
            return a;
        });
        when(activityRepository.existsByTripIdAndSourceProviderAndSourceItemId(any(), any(), any()))
            .thenAnswer(call -> savedActivities.stream().anyMatch(a ->
                a.getSourceItemId() != null && a.getSourceItemId().equals(call.getArgument(2))));
        when(activityRepository.findSourceItemIds(any(), any())).thenAnswer(call ->
            savedActivities.stream().map(ItineraryActivity::getSourceItemId).distinct().toList());

        when(budgetRepository.findByTripId(TRIP_ID)).thenAnswer(call -> Optional.ofNullable(budget));
        when(budgetRepository.save(any())).thenAnswer(call -> {
            TripBudget b = call.getArgument(0);
            if (b != null) {
                budget = b;
                budgetSaves++;
            }
            return b;
        });
        when(budgetRepository.lockTrip(TRIP_ID)).thenAnswer(call -> {
            locks++;
            return Optional.of(f.trip);
        });
        when(budgetRepository.findTripCurrency(TRIP_ID)).thenAnswer(call -> Optional.of(f.trip.getCurrency()));
    }

    // An existing budget
    TripBudget budget(String currencyCode, String total, Map<String, String> allocations) {
        TripBudget b = new TripBudget(TRIP_ID);
        b.setCurrency(currencyCode);
        b.setTotalAmount(new BigDecimal(total));
        allocations.forEach((k, v) -> b.getAllocations().put(k, new BigDecimal(v)));
        budget = b;
        return b;
    }

    // Item IDs from a real search for the trip
    String hotelId(int index) {
        return search(TripItemType.HOTEL).get(index).itemId();
    }

    String flightId(int index) {
        return search(TripItemType.FLIGHT).get(index).itemId();
    }

    String restaurantId(int index) {
        return search(TripItemType.RESTAURANT).get(index).itemId();
    }

    String activityId(int index) {
        return search(TripItemType.ACTIVITY).get(index).itemId();
    }

    List<ProviderItem> search(TripItemType type) {
        boolean range = type == TripItemType.FLIGHT || type == TripItemType.HOTEL;
        return provider.search(new SearchCriteria(type, f.trip.getDestination(), f.trip.getStartDate(),
            range ? f.trip.getEndDate() : null, f.trip.getNumberOfTravelers(),
            type == TripItemType.FLIGHT ? "ORD" : null, null, null, null, null));
    }

    static void assertMoney(String expected, BigDecimal actual) {
        if (actual == null || new BigDecimal(expected).compareTo(actual) != 0) {
            throw new AssertionError("expected " + expected + " but was " + actual);
        }
    }
}
