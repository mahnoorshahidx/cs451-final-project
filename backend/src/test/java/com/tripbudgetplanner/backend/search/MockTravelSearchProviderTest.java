package com.tripbudgetplanner.backend.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.tripbudgetplanner.backend.currency.SupportedCurrency;

// UC6: the demo provider
class MockTravelSearchProviderTest {

    private static final LocalDate START = LocalDate.of(2027, 6, 1);
    private static final LocalDate END = LocalDate.of(2027, 6, 10);

    private final MockTravelSearchProvider provider = new MockTravelSearchProvider();

    @Test
    void sameSearchGivesTheSameResults() {
        List<ProviderItem> first = provider.search(hotels("Tokyo", 3));
        List<ProviderItem> second = provider.search(hotels("Tokyo", 3));

        assertEquals(8, first.size());
        assertEquals(first, second);
    }

    @Test
    void everyResultIsLabeledDemoAndPricedInUsd() {
        for (TripItemType type : TripItemType.values()) {
            for (ProviderItem item : provider.search(criteria(type, "Paris", 2))) {
                assertTrue(item.itemId().startsWith("demo-"), item.itemId());
                assertEquals(SupportedCurrency.USD, item.priceCurrency());
                assertTrue(item.unitPrice().signum() > 0);
                assertEquals(2, item.unitPrice().scale());
                if (type != TripItemType.FLIGHT) {
                    assertTrue(item.name().endsWith("(demo)"), item.name());
                }
            }
        }
        assertEquals("demo", provider.name());
        assertFalse(provider.live());
    }

    @Test
    void findItemRebuildsTheExactItem() {
        for (TripItemType type : TripItemType.values()) {
            for (ProviderItem item : provider.search(criteria(type, "Rome", 4))) {
                assertEquals(item, provider.findItem(item.itemId()).orElseThrow());
            }
        }
    }

    @Test
    void malformedOrEditedIdsAreNotFound() {
        String good = provider.search(hotels("Tokyo", 3)).get(0).itemId();

        assertTrue(provider.findItem(good.replace("-0", "-99")).isEmpty(), "index out of range");
        assertTrue(provider.findItem(good.replace("-3-", "-03-")).isEmpty(), "leading zero");
        assertTrue(provider.findItem("demo-hotel-tokyo-20270610-20270601-3-x-0").isEmpty(), "check-out before check-in");
        assertTrue(provider.findItem("demo-flight-tokyo-20270601-20270610-3-x-0").isEmpty(), "flight without origin");
        assertTrue(provider.findItem("demo-hotel-tokyo-20270601-x-3-x-0").isEmpty(), "hotel without check-out");
        assertTrue(provider.findItem("demo-hotel-tokyo-20271340-20271341-3-x-0").isEmpty(), "invalid date");
        assertTrue(provider.findItem("demo-car-tokyo-20270601-x-3-x-0").isEmpty());
        assertTrue(provider.findItem("<script>").isEmpty());
        assertTrue(provider.findItem(null).isEmpty());
        assertTrue(provider.findItem("x".repeat(200)).isEmpty());
    }

    @Test
    void pricesFollowDestinationCost() {
        BigDecimal london = averageNightly("London");
        BigDecimal bangkok = averageNightly("Bangkok");

        assertTrue(london.compareTo(bangkok.multiply(BigDecimal.valueOf(2))) > 0,
            "London " + london + " should cost much more than Bangkok " + bangkok);
    }

    @Test
    void unknownDestinationsStillGetResults() {
        List<ProviderItem> items = provider.search(criteria(TripItemType.ACTIVITY, "Reykjavik", 2));

        assertEquals(10, items.size());
        assertTrue(items.get(0).name().startsWith("Reykjavik"));
    }

    @Test
    void destinationKeyIgnoresAccentsCaseAndPunctuation() {
        assertEquals("cancun_mexico", provider.destinationKey("  Cancún, MEXICO "));
        assertEquals("new_york", provider.destinationKey("New York"));
        assertEquals("trip", provider.destinationKey("!!!"));
        assertEquals(40, provider.destinationKey("a".repeat(80)).length());
    }

    @Test
    void hotelPricingIsPerRoomPerNight() {
        ProviderItem hotel = provider.search(hotels("Tokyo", 3)).get(0);

        // 3 travelers -> 2 rooms, 9 nights
        assertEquals(PriceBasis.PER_ROOM_PER_NIGHT, hotel.priceBasis());
        assertEquals(18, hotel.quantity());
        assertEquals("2 rooms x 9 nights", hotel.quantityDescription());
    }

    @Test
    void flightsArePerPersonRoundTripWithBothLegs() {
        ProviderItem flight = provider.search(criteria(TripItemType.FLIGHT, "Tokyo", 3)).get(0);

        assertEquals(PriceBasis.PER_PERSON_ROUND_TRIP, flight.priceBasis());
        assertEquals(3, flight.quantity());
        assertEquals(START, flight.date());
        assertEquals(END, flight.endDate());
        assertEquals("ORD", flight.details().get("from"));
        assertEquals("HND", flight.details().get("to"));
        assertTrue(flight.returnStartTime() != null);
    }

    @Test
    void privateToursArePricedForTheGroup() {
        ProviderItem guide = provider.search(criteria(TripItemType.ACTIVITY, "Tokyo", 4)).stream()
            .filter(i -> i.priceBasis() == PriceBasis.PER_GROUP).findFirst().orElseThrow();

        assertEquals(1, guide.quantity());
        assertEquals("whole group", guide.quantityDescription());
    }

    @Test
    void travelersChangeQuantitiesButNotPrices() {
        ProviderItem two = provider.search(criteria(TripItemType.RESTAURANT, "Seoul", 2)).get(0);
        ProviderItem five = provider.search(criteria(TripItemType.RESTAURANT, "Seoul", 5)).get(0);

        assertEquals(two.unitPrice(), five.unitPrice());
        assertEquals(5, five.quantity());
        assertNotEquals(two.itemId(), five.itemId(), "the ID records the group size");
    }

    @Test
    void filtersWork() {
        SearchCriteria fancy = new SearchCriteria(TripItemType.RESTAURANT, "Tokyo", START, null, 2,
            null, null, 4, null, null);
        provider.search(fancy).forEach(i -> assertEquals("$$$$", i.details().get("priceLevel")));

        SearchCriteria rated = new SearchCriteria(TripItemType.HOTEL, "Tokyo", START, END, 2,
            null, null, null, null, new BigDecimal("4.5"));
        provider.search(rated).forEach(i -> assertTrue(i.rating().compareTo(new BigDecimal("4.5")) >= 0));

        SearchCriteria keyword = new SearchCriteria(TripItemType.ACTIVITY, "Tokyo", START, null, 2,
            null, null, null, "COOKING", null);
        List<ProviderItem> cooking = provider.search(keyword);
        assertEquals(1, cooking.size());
        assertTrue(cooking.get(0).name().contains("cooking class"));
    }

    @Test
    void lateFlightsLandTheNextDay() {
        for (ProviderItem f : provider.search(criteria(TripItemType.FLIGHT, "Sydney", 1))) {
            if (f.endTime() != null) {
                assertTrue(f.endTime().isAfter(f.startTime()));
            }
        }
        // A 1500-USD route is long, so at least one option lands the next day
        assertTrue(provider.search(criteria(TripItemType.FLIGHT, "Sydney", 1)).stream()
            .anyMatch(f -> f.endTime() == null || f.returnEndTime() == null));
    }

    // ---------- Helpers ----------

    private BigDecimal averageNightly(String city) {
        List<ProviderItem> items = provider.search(hotels(city, 2));
        return items.stream().map(ProviderItem::unitPrice).reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(items.size()), 2, RoundingMode.HALF_UP);
    }

    private static SearchCriteria hotels(String destination, int travelers) {
        return criteria(TripItemType.HOTEL, destination, travelers);
    }

    private static SearchCriteria criteria(TripItemType type, String destination, int travelers) {
        boolean range = type == TripItemType.FLIGHT || type == TripItemType.HOTEL;
        return new SearchCriteria(type, destination, START, range ? END : null, travelers,
            type == TripItemType.FLIGHT ? "ORD" : null, null, null, null, null);
    }
}
