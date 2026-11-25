package com.tripbudgetplanner.backend.search;

import static com.tripbudgetplanner.backend.member.SharedTripFixture.*;
import static com.tripbudgetplanner.backend.search.SearchTestSetup.assertMoney;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.tripbudgetplanner.backend.budget.exception.BudgetConflictException;
import com.tripbudgetplanner.backend.itinerary.ItineraryActivity;
import com.tripbudgetplanner.backend.itinerary.dto.ItineraryActivityResponse;
import com.tripbudgetplanner.backend.search.dto.AddTripItemRequest;
import com.tripbudgetplanner.backend.search.dto.AddTripItemResponse;
import com.tripbudgetplanner.backend.search.exception.DuplicateTripItemException;
import com.tripbudgetplanner.backend.search.exception.SearchItemMismatchException;
import com.tripbudgetplanner.backend.search.exception.SearchItemNotFoundException;
import com.tripbudgetplanner.backend.trip.exception.TripAccessDeniedException;
import com.tripbudgetplanner.backend.trip.exception.TripNotFoundException;

// UC6: adding search results to the itinerary and (optionally) the budget
class TripItemServiceTest {

    private final SearchTestSetup s = new SearchTestSetup();

    // ---------- Adding each type ----------

    @Test
    void addsAHotelWithItsEstimatedCostAndLeavesTheBudgetAloneByDefault() {
        ProviderItem hotel = s.search(TripItemType.HOTEL).get(0);

        AddTripItemResponse r = s.itemService.addItem(TRIP_ID, request(hotel.itemId(), null), EDITOR_ID);

        assertEquals(1, r.activities().size());
        ItineraryActivityResponse a = r.activities().get(0);
        assertEquals("Check in: " + hotel.name(), a.title());
        assertEquals("Accommodation", a.category());
        assertEquals(LocalDate.of(2027, 6, 1), a.activityDate());
        assertEquals("HOTEL", a.sourceType());
        assertEquals("demo", a.sourceProvider());
        assertEquals("MAIN", a.sourceSegment());
        assertEquals("USD", a.costCurrency());
        // 2 rooms x 9 nights at the nightly price
        assertMoney(hotel.unitPrice().multiply(BigDecimal.valueOf(18)).toPlainString(), a.estimatedCost());
        assertTrue(a.notes().contains("2 rooms x 9 nights"));
        assertTrue(a.notes().contains("Demo data"));
        assertNull(a.budgetAmountAdded());
        assertNull(r.budgetChange(), "updateBudget defaults to false");
        assertNull(s.budget);
        assertTrue(r.item().alreadyAdded());
        assertEquals(TravelSearchService.DEMO_NOTICE, r.notice());
    }

    @Test
    void roundTripFlightCreatesTwoEntriesButCountsTheCostOnce() {
        ProviderItem flight = s.search(TripItemType.FLIGHT).get(0);

        AddTripItemResponse r = s.itemService.addItem(TRIP_ID, request(flight.itemId(), true), OWNER_ID);

        assertEquals(2, r.activities().size());
        ItineraryActivityResponse out = r.activities().get(0);
        ItineraryActivityResponse back = r.activities().get(1);
        assertEquals("OUTBOUND", out.sourceSegment());
        assertEquals("RETURN", back.sourceSegment());
        assertEquals(LocalDate.of(2027, 6, 1), out.activityDate());
        assertEquals(LocalDate.of(2027, 6, 10), back.activityDate());
        assertEquals("Transportation", out.category());
        assertTrue(out.title().startsWith("Flight ORD"));
        assertTrue(back.title().startsWith("Return flight HND"));

        // Per person x 3 travelers, stored once
        BigDecimal total = flight.unitPrice().multiply(BigDecimal.valueOf(3));
        assertMoney(total.toPlainString(), out.estimatedCost());
        assertNull(back.estimatedCost());
        assertTrue(back.notes().contains("Price included with the outbound flight"));
        assertMoney(total.toPlainString(), out.budgetAmountAdded());
        assertNull(back.budgetAmountAdded());

        // The budget grew by the total once
        assertMoney(total.toPlainString(), s.budget.getAllocations().get("Flights"));
        assertEquals(1, s.budgetSaves);
    }

    @Test
    void addsRestaurantsAndActivitiesWithTheirTimes() {
        ProviderItem dinner = s.search(TripItemType.RESTAURANT).get(1);
        ItineraryActivityResponse food = s.itemService.addItem(TRIP_ID, request(dinner.itemId(), false), OWNER_ID)
            .activities().get(0);
        assertEquals("Food & Drink", food.category());
        assertEquals(dinner.startTime(), food.startTime());
        assertEquals(dinner.endTime(), food.endTime());

        ProviderItem tour = s.search(TripItemType.ACTIVITY).get(0);
        ItineraryActivityResponse act = s.itemService.addItem(TRIP_ID, request(tour.itemId(), false), OWNER_ID)
            .activities().get(0);
        assertEquals("Activity", act.category());
        assertEquals(dinner.date(), act.activityDate());
    }

    @Test
    void startTimeCanBeMovedForRestaurantsKeepingTheLength() {
        ProviderItem dinner = s.search(TripItemType.RESTAURANT).get(0);

        ItineraryActivityResponse a = s.itemService.addItem(TRIP_ID,
            new AddTripItemRequest(dinner.itemId(), false, LocalTime.of(20, 0), "  Window seat please "), OWNER_ID)
            .activities().get(0);

        assertEquals(LocalTime.of(20, 0), a.startTime());
        assertEquals(LocalTime.of(21, 30), a.endTime(), "restaurants last 90 minutes");
        assertTrue(a.notes().endsWith("Window seat please"));
    }

    @Test
    void startTimeCantBeChangedForFlightsOrHotelsOrPastMidnight() {
        assertThrows(IllegalArgumentException.class, () -> s.itemService.addItem(TRIP_ID,
            new AddTripItemRequest(s.flightId(0), false, LocalTime.NOON, null), OWNER_ID));
        assertThrows(IllegalArgumentException.class, () -> s.itemService.addItem(TRIP_ID,
            new AddTripItemRequest(s.hotelId(0), false, LocalTime.NOON, null), OWNER_ID));
        assertThrows(IllegalArgumentException.class, () -> s.itemService.addItem(TRIP_ID,
            new AddTripItemRequest(s.restaurantId(0), false, LocalTime.of(23, 30), null), OWNER_ID));
        assertTrue(s.savedActivities.isEmpty());
    }

    // ---------- Permissions ----------

    @Test
    void viewersCantAddAndStrangersGet404() {
        assertThrows(TripAccessDeniedException.class,
            () -> s.itemService.addItem(TRIP_ID, request(s.hotelId(0), true), VIEWER_ID));
        assertThrows(TripNotFoundException.class,
            () -> s.itemService.addItem(TRIP_ID, request(s.hotelId(0), true), STRANGER_ID));
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
            () -> s.itemService.addItem(TRIP_ID, request(s.hotelId(0), true), null));
        assertEquals(HttpStatus.UNAUTHORIZED, e.getStatusCode());

        assertTrue(s.savedActivities.isEmpty());
        assertNull(s.budget);
        assertEquals(0, s.locks, "no lock is taken before the permission check");
    }

    // ---------- Don't trust the item ID ----------

    @Test
    void unknownOrMalformedIdsAre404() {
        assertThrows(SearchItemNotFoundException.class,
            () -> s.itemService.addItem(TRIP_ID, request("demo-hotel-tokyo-20270601-20270610-3-x-42", false), OWNER_ID));
        assertThrows(SearchItemNotFoundException.class,
            () -> s.itemService.addItem(TRIP_ID, request("hotel; DROP TABLE trips", false), OWNER_ID));
    }

    @Test
    void itemsForAnotherDestinationAreRejected() {
        String parisHotel = s.hotelId(0).replace("-tokyo-", "-paris-");

        SearchItemMismatchException e = assertThrows(SearchItemMismatchException.class,
            () -> s.itemService.addItem(TRIP_ID, request(parisHotel, true), OWNER_ID));
        assertTrue(e.getMessage().contains("different destination"));
        assertTrue(s.savedActivities.isEmpty());
        assertNull(s.budget);
    }

    @Test
    void itemsPricedForADifferentGroupSizeAreRejected() {
        // A real search result for 1 traveler would be much cheaper than for 3
        String cheaper = s.restaurantId(0).replace("-3-x-", "-1-x-");

        SearchItemMismatchException e = assertThrows(SearchItemMismatchException.class,
            () -> s.itemService.addItem(TRIP_ID, request(cheaper, false), OWNER_ID));
        assertTrue(e.getMessage().contains("1 travelers"));
    }

    @Test
    void itemsOutsideTheTripDatesAreRejected() {
        String before = s.restaurantId(0).replace("-20270601-", "-20270530-");
        String longStay = s.hotelId(0).replace("-20270610-", "-20270615-");

        assertThrows(SearchItemMismatchException.class, () -> s.itemService.addItem(TRIP_ID, request(before, false), OWNER_ID));
        assertThrows(SearchItemMismatchException.class, () -> s.itemService.addItem(TRIP_ID, request(longStay, false), OWNER_ID));
    }

    @Test
    void resultsBecomeStaleWhenTheTripChanges() {
        String id = s.hotelId(0);
        s.f.trip.setNumberOfTravelers(4);

        assertThrows(SearchItemMismatchException.class, () -> s.itemService.addItem(TRIP_ID, request(id, false), OWNER_ID));
    }

    // ---------- Duplicates ----------

    @Test
    void sameItemCantBeAddedTwice() {
        String id = s.flightId(1);
        s.itemService.addItem(TRIP_ID, request(id, true), OWNER_ID);
        BigDecimal afterFirst = s.budget.getAllocations().get("Flights");

        assertThrows(DuplicateTripItemException.class, () -> s.itemService.addItem(TRIP_ID, request(id, true), EDITOR_ID));
        assertEquals(2, s.savedActivities.size(), "still only the two flight legs");
        assertMoney(afterFirst.toPlainString(), s.budget.getAllocations().get("Flights"));
    }

    @Test
    void databaseDuplicateBecomes409() {
        // Another request saved it between our check and our insert
        when(s.activityRepository.saveAndFlush(any()))
            .thenThrow(new DataIntegrityViolationException("uq_itinerary_source_item"));

        assertThrows(DuplicateTripItemException.class,
            () -> s.itemService.addItem(TRIP_ID, request(s.hotelId(0), false), OWNER_ID));
    }

    @Test
    void addingTakesTheTripLockFirst() {
        s.itemService.addItem(TRIP_ID, request(s.hotelId(0), true), OWNER_ID);

        assertTrue(s.locks >= 1);
    }

    // ---------- Budget updates ----------

    @Test
    void createsABudgetInTheTripCurrencyWhenThereIsNone() {
        s.f.trip.setCurrency("JPY");
        ProviderItem hotel = s.search(TripItemType.HOTEL).get(0);

        AddTripItemResponse r = s.itemService.addItem(TRIP_ID, request(hotel.itemId(), true), OWNER_ID);

        assertTrue(r.budgetChange().budgetCreated());
        assertEquals("JPY", s.budget.getCurrency());
        assertEquals("Accommodation", r.budgetChange().category());
        assertEquals("JPY", r.budgetChange().currency());
        assertNull(r.budgetChange().conversion(), "item already priced in JPY");
        BigDecimal total = r.activities().get(0).estimatedCost();
        assertEquals(0, total.scale(), "no decimals for yen");
        assertMoney(total.toPlainString(), s.budget.getTotalAmount());
        assertMoney(total.toPlainString(), r.budgetChange().amountAdded());
        assertEquals("JPY", r.activities().get(0).budgetAmountCurrency());
    }

    @Test
    void addsToAnExistingCategoryAndKeepsTheTotalWhenThereIsRoom() {
        s.budget("USD", "10000.00", Map.of("Accommodation", "500.00", "Food", "300.00"));
        ProviderItem hotel = s.search(TripItemType.HOTEL).get(0);
        BigDecimal cost = hotel.unitPrice().multiply(BigDecimal.valueOf(18));

        AddTripItemResponse r = s.itemService.addItem(TRIP_ID, request(hotel.itemId(), true), EDITOR_ID);

        assertMoney("500.00", r.budgetChange().allocationBefore());
        assertMoney(cost.add(new BigDecimal("500")).toPlainString(), s.budget.getAllocations().get("Accommodation"));
        assertMoney("10000.00", s.budget.getTotalAmount());
        assertMoney("10000.00", r.budgetChange().totalAfter());
        assertFalse(r.budgetChange().budgetCreated());
        assertEquals(1L, s.budget.getVersion(), "every change bumps the version");
    }

    @Test
    void reusesAMatchingCategoryName() {
        s.budget("USD", "10000.00", Map.of("flight", "100.00"));

        AddTripItemResponse r = s.itemService.addItem(TRIP_ID, request(s.flightId(0), true), OWNER_ID);

        assertEquals("flight", r.budgetChange().category());
        assertFalse(s.budget.getAllocations().containsKey("Flights"), "no second flights category");
    }

    @Test
    void raisesTheTotalWhenAllocationsWouldExceedIt() {
        s.budget("USD", "1000.00", Map.of("Food", "900.00"));
        ProviderItem tour = s.search(TripItemType.ACTIVITY).get(0);
        BigDecimal cost = tour.unitPrice().multiply(BigDecimal.valueOf(tour.quantity()));

        AddTripItemResponse r = s.itemService.addItem(TRIP_ID, request(tour.itemId(), true), OWNER_ID);

        assertMoney(cost.add(new BigDecimal("900")).toPlainString(), s.budget.getTotalAmount());
        assertMoney("1000.00", r.budgetChange().totalBefore());
        assertMoney(cost.toPlainString(), s.budget.getAllocations().get("Activities"));
    }

    @Test
    void convertsIntoABudgetInAnotherCurrencyWithTheSameSnapshot() {
        // USD trip, EUR budget: 1 USD = 0.92 EUR with the demo rates
        s.budget("EUR", "5000.00", Map.of());
        ProviderItem dinner = s.search(TripItemType.RESTAURANT).get(0);
        BigDecimal usd = dinner.unitPrice().multiply(BigDecimal.valueOf(3));

        AddTripItemResponse r = s.itemService.addItem(TRIP_ID, request(dinner.itemId(), true), OWNER_ID);

        assertEquals("EUR", r.budgetChange().currency());
        assertMoney(usd.multiply(new BigDecimal("0.92")).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(),
            r.budgetChange().amountAdded());
        assertNotNull(r.budgetChange().conversion());
        assertEquals("fallback", r.budgetChange().conversion().source());
        assertEquals("EUR", r.activities().get(0).budgetAmountCurrency());
        assertEquals("USD", r.activities().get(0).costCurrency(), "the itinerary keeps the trip's currency");
    }

    @Test
    void unsupportedBudgetCurrencyIs409AndNothingIsSaved() {
        s.budget("MAD", "20000.00", Map.of("Food", "1000.00"));

        BudgetConflictException e = assertThrows(BudgetConflictException.class,
            () -> s.itemService.addItem(TRIP_ID, request(s.restaurantId(0), true), OWNER_ID));

        assertTrue(e.getMessage().contains("MAD"));
        assertTrue(s.savedActivities.isEmpty(), "the activity isn't added either");
        assertMoney("1000.00", s.budget.getAllocations().get("Food"));
        assertEquals(0, s.budgetSaves);

        // Without the budget update it works
        assertEquals(1, s.itemService.addItem(TRIP_ID, request(s.restaurantId(0), false), OWNER_ID).activities().size());
    }

    @Test
    void budgetIsNotReducedWhenAnActivityIsDeletedLater() {
        // Documented behavior: deletion doesn't reverse budget additions
        s.itemService.addItem(TRIP_ID, request(s.hotelId(1), true), OWNER_ID);
        BigDecimal total = s.budget.getTotalAmount();
        ItineraryActivity added = s.savedActivities.get(0);

        assertNotNull(added.getBudgetAmountAdded(), "kept so the page can show what was added");
        assertMoney(total.toPlainString(), added.getBudgetAmountAdded());
    }

    // ---------- Helpers ----------

    private static AddTripItemRequest request(String itemId, Boolean updateBudget) {
        return new AddTripItemRequest(itemId, updateBudget, null, null);
    }
}
