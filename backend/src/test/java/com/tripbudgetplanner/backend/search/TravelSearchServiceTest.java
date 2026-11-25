package com.tripbudgetplanner.backend.search;

import static com.tripbudgetplanner.backend.member.SharedTripFixture.*;
import static com.tripbudgetplanner.backend.search.SearchTestSetup.assertMoney;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.tripbudgetplanner.backend.itinerary.ItineraryActivity;
import com.tripbudgetplanner.backend.search.TravelSearchService.SearchParams;
import com.tripbudgetplanner.backend.search.dto.SearchResponse;
import com.tripbudgetplanner.backend.search.dto.SearchResult;
import com.tripbudgetplanner.backend.trip.exception.TripNotFoundException;

// UC6: searching, validation, prices in the trip's currency, and who can search
class TravelSearchServiceTest {

    private final SearchTestSetup s = new SearchTestSetup();

    // ---------- Who can search ----------

    @Test
    void everyMemberCanSearchIncludingViewers() {
        for (Long user : List.of(OWNER_ID, EDITOR_ID, VIEWER_ID)) {
            assertFalse(s.searchService.search(TRIP_ID, TripItemType.HOTEL, empty(), user).results().isEmpty());
        }
    }

    @Test
    void strangersGet404AndSignedOutUsersGet401() {
        assertThrows(TripNotFoundException.class,
            () -> s.searchService.search(TRIP_ID, TripItemType.HOTEL, empty(), STRANGER_ID));
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
            () -> s.searchService.search(TRIP_ID, TripItemType.HOTEL, empty(), null));
        assertEquals(HttpStatus.UNAUTHORIZED, e.getStatusCode());
    }

    // ---------- Defaults and labels ----------

    @Test
    void usesTheTripsDestinationDatesTravelersAndCurrency() {
        SearchResponse r = s.searchService.search(TRIP_ID, TripItemType.HOTEL, empty(), VIEWER_ID);

        assertEquals("Tokyo", r.destination());
        assertEquals("USD", r.currency());
        assertEquals(3, r.travelers());
        assertEquals("demo", r.source());
        assertFalse(r.live());
        assertEquals(TravelSearchService.DEMO_NOTICE, r.notice());
        assertNull(r.exchangeRate(), "USD trip, USD prices: nothing to convert");
        SearchResult first = r.results().get(0);
        assertEquals(LocalDate.of(2027, 6, 1), first.date());
        assertEquals(LocalDate.of(2027, 6, 10), first.endDate());
        assertEquals("demo", first.source());
        assertEquals("Accommodation", first.budgetCategory());
    }

    @Test
    void everyResultSaysWhatThePriceCovers() {
        for (TripItemType type : TripItemType.values()) {
            for (SearchResult r : s.searchService.search(TRIP_ID, type, type == TripItemType.FLIGHT ? flights("ORD") : empty(),
                    OWNER_ID).results()) {
                assertNotNull(r.priceBasis());
                assertNotNull(r.priceBasisLabel());
                assertNotNull(r.quantityDescription());
                assertMoney(r.unitPrice().multiply(BigDecimal.valueOf(r.quantity())).toPlainString(), r.estimatedTotal());
            }
        }
    }

    // ---------- Currency ----------

    @Test
    void pricesAreConvertedToTheTripCurrencyWithZeroDecimalsForYen() {
        s.f.trip.setCurrency("JPY");

        SearchResponse r = s.searchService.search(TRIP_ID, TripItemType.RESTAURANT, empty(), OWNER_ID);

        assertEquals("JPY", r.currency());
        assertEquals("USD", r.exchangeRate().from());
        assertEquals("fallback", r.exchangeRate().source());
        assertNotNull(r.exchangeRate().notice(), "demo exchange rates are labeled");
        for (SearchResult x : r.results()) {
            assertEquals(0, x.unitPrice().scale());
            assertEquals("JPY", x.currency());
            assertEquals("USD", x.originalCurrency());
            // 1 USD = 150 JPY with the fixed demo rates
            assertMoney(x.originalUnitPrice().multiply(BigDecimal.valueOf(150))
                .setScale(0, java.math.RoundingMode.HALF_UP).toPlainString(), x.unitPrice());
        }
    }

    @Test
    void maxPriceIsInTheTripCurrency() {
        s.f.trip.setCurrency("JPY");
        List<SearchResult> all = s.searchService.search(TRIP_ID, TripItemType.HOTEL, empty(), OWNER_ID).results();
        BigDecimal cheapest = all.stream().map(SearchResult::unitPrice).min(Comparator.naturalOrder()).orElseThrow();

        // A limit in yen that only the cheapest hotel fits under
        List<SearchResult> filtered = s.searchService.search(TRIP_ID, TripItemType.HOTEL,
            withMaxPrice(cheapest.toPlainString()), OWNER_ID).results();

        assertFalse(filtered.isEmpty());
        filtered.forEach(x -> assertTrue(x.unitPrice().compareTo(cheapest) <= 0));
        // The same number as dollars would have kept every hotel
        assertTrue(filtered.size() < all.size());
    }

    // ---------- Sorting, limit, alreadyAdded ----------

    @Test
    void sortsByPriceOrRatingAndLimits() {
        List<SearchResult> byPrice = s.searchService.search(TRIP_ID, TripItemType.ACTIVITY,
            params(null, null, null, null, null, null, null, null, null, null, null, "price", "5"), OWNER_ID).results();
        assertEquals(5, byPrice.size());
        for (int i = 1; i < byPrice.size(); i++) {
            assertTrue(byPrice.get(i - 1).unitPrice().compareTo(byPrice.get(i).unitPrice()) <= 0);
        }

        List<SearchResult> byRating = s.searchService.search(TRIP_ID, TripItemType.HOTEL,
            params(null, null, null, null, null, null, null, null, null, null, null, "rating", null), OWNER_ID).results();
        for (int i = 1; i < byRating.size(); i++) {
            assertTrue(byRating.get(i - 1).rating().compareTo(byRating.get(i).rating()) >= 0);
        }
    }

    @Test
    void marksResultsAlreadyInTheItinerary() {
        String id = s.hotelId(2);
        ItineraryActivity existing = new ItineraryActivity();
        existing.setSource("HOTEL", "demo", id, "MAIN");
        s.savedActivities.add(existing);

        List<SearchResult> results = s.searchService.search(TRIP_ID, TripItemType.HOTEL, empty(), OWNER_ID).results();

        results.forEach(r -> assertEquals(r.itemId().equals(id), r.alreadyAdded()));
    }

    // ---------- Validation ----------

    @Test
    void flightsNeedAValidOrigin() {
        assertBad(TripItemType.FLIGHT, empty(), "origin is required");
        assertBad(TripItemType.FLIGHT, flights("Chicago"), "origin is required");
        assertEquals(6, s.searchService.search(TRIP_ID, TripItemType.FLIGHT, flights("ord"), OWNER_ID).results().size());
    }

    @Test
    void datesMustBeValidAndInsideTheTrip() {
        assertBad(TripItemType.RESTAURANT, onDate("2027-05-31"), "within the trip dates");
        assertBad(TripItemType.RESTAURANT, onDate("2027-06-11"), "within the trip dates");
        assertBad(TripItemType.RESTAURANT, onDate("June 2nd"), "must be a date");
        assertBad(TripItemType.HOTEL, params(null, null, null, "2027-06-05", "2027-06-05",
            null, null, null, null, null, null, null, null), "at least one night");
        assertBad(TripItemType.FLIGHT, params("ORD", "2027-06-08", "2027-06-03",
            null, null, null, null, null, null, null, null, null, null), "returnDate can't be before");
    }

    @Test
    void numbersAndTextAreChecked() {
        assertBad(TripItemType.HOTEL, withMaxPrice("-5"), "maxPrice");
        assertBad(TripItemType.HOTEL, withMaxPrice("10.555"), "maxPrice");
        assertBad(TripItemType.HOTEL, withMaxPrice("cheap"), "maxPrice must be a number");
        assertBad(TripItemType.HOTEL, params(null, null, null, null, null, null, null, null, null, "6",
            null, null, null), "minRating");
        assertBad(TripItemType.RESTAURANT, params(null, null, null, null, null, null, null, "5", null, null,
            null, null, null), "priceLevel");
        assertBad(TripItemType.HOTEL, params(null, null, null, null, null, null, null, null, null, null,
            null, "cheapest", null), "sort must be");
        assertBad(TripItemType.HOTEL, params(null, null, null, null, null, null, null, null, null, null,
            null, null, "50"), "limit");
        assertBad(TripItemType.ACTIVITY, params(null, null, null, null, null, null, null, null, "x".repeat(51),
            null, null, null, null), "keyword");
    }

    @Test
    void providerInfoSaysDemo() {
        assertEquals("demo", s.searchService.providerInfo().provider());
        assertFalse(s.searchService.providerInfo().live());
        assertEquals(4, s.searchService.providerInfo().types().size());
    }

    // ---------- Helpers ----------

    private void assertBad(TripItemType type, SearchParams p, String messagePart) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> s.searchService.search(TRIP_ID, type, p, OWNER_ID));
        assertTrue(e.getMessage().contains(messagePart), e.getMessage());
    }

    private static SearchParams empty() {
        return params(null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    private static SearchParams flights(String origin) {
        return params(origin, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    private static SearchParams onDate(String date) {
        return params(null, null, null, null, null, date, null, null, null, null, null, null, null);
    }

    private static SearchParams withMaxPrice(String maxPrice) {
        return params(null, null, null, null, null, null, null, null, null, null, maxPrice, null, null);
    }

    private static SearchParams params(String origin, String departDate, String returnDate, String checkIn,
                                       String checkOut, String date, String cuisine, String priceLevel,
                                       String keyword, String minRating, String maxPrice, String sort, String limit) {
        return new SearchParams(origin, departDate, returnDate, checkIn, checkOut, date, cuisine, priceLevel,
            keyword, minRating, maxPrice, sort, limit);
    }
}
