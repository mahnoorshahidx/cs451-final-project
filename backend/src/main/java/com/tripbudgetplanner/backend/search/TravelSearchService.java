package com.tripbudgetplanner.backend.search;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tripbudgetplanner.backend.currency.CurrencyService;
import com.tripbudgetplanner.backend.currency.ExchangeRates;
import com.tripbudgetplanner.backend.currency.SupportedCurrency;
import com.tripbudgetplanner.backend.currency.dto.ConversionResponse;
import com.tripbudgetplanner.backend.currency.dto.RateInfo;
import com.tripbudgetplanner.backend.itinerary.ItineraryActivityRepository;
import com.tripbudgetplanner.backend.search.dto.SearchProviderResponse;
import com.tripbudgetplanner.backend.search.dto.SearchResponse;
import com.tripbudgetplanner.backend.search.dto.SearchResult;
import com.tripbudgetplanner.backend.trip.Trip;
import com.tripbudgetplanner.backend.trip.TripAccessService;

// UC6: search flights, hotels, restaurants and activities for a trip.
// Any trip member (owner, editor or viewer) can search; searching changes nothing.
// Destination, dates, travelers and currency come from the trip. Prices are converted
// to the trip's currency with one exchange-rate snapshot, and maxPrice is applied after that.
@Service
public class TravelSearchService {

    static final int DEFAULT_LIMIT = 10;
    static final int MAX_LIMIT = 20;
    static final int MAX_TEXT = 50;

    static final String DEMO_NOTICE = "Demo data for development, not live prices or availability.";

    private final TravelSearchProvider provider;
    private final TripAccessService tripAccess;
    private final CurrencyService currencyService;
    private final ItineraryActivityRepository activityRepository;

    public TravelSearchService(TravelSearchProvider provider, TripAccessService tripAccess,
                               CurrencyService currencyService, ItineraryActivityRepository activityRepository) {
        this.provider = provider;
        this.tripAccess = tripAccess;
        this.currencyService = currencyService;
        this.activityRepository = activityRepository;
    }

    // Raw query parameters, checked here so every mistake gets a clear 400 message
    public record SearchParams(
        String origin, String departDate, String returnDate,
        String checkIn, String checkOut, String date,
        String cuisine, String priceLevel, String keyword, String minRating,
        String maxPrice, String sort, String limit
    ) {}

    public SearchProviderResponse providerInfo() {
        return new SearchProviderResponse(provider.name(), provider.live(),
            Arrays.stream(TripItemType.values()).map(Enum::name).toList(),
            provider.live() ? null : DEMO_NOTICE);
    }

    @Transactional(readOnly = true)
    public SearchResponse search(Long tripId, TripItemType type, SearchParams p, Long userId) {
        Trip trip = tripAccess.viewableTrip(tripId, userId);
        SupportedCurrency currency = SupportedCurrency.fromCode(trip.getCurrency());

        SearchCriteria criteria = criteria(type, p, trip);
        BigDecimal maxPrice = parseMaxPrice(p.maxPrice());
        String sort = parseSort(p.sort());
        int limit = parseLimit(p.limit());

        List<ProviderItem> found = provider.search(criteria);

        // One snapshot of rates for the whole search (only loaded if something needs converting)
        boolean needsRates = found.stream().anyMatch(item -> item.priceCurrency() != currency);
        ExchangeRates rates = needsRates ? currencyService.currentRates() : null;
        Set<String> added = new HashSet<>(activityRepository.findSourceItemIds(tripId, provider.name()));

        List<Priced> priced = found.stream()
            .map(item -> price(item, currency, rates, added.contains(item.itemId())))
            // maxPrice is in the trip's currency and applies to unitPrice (see priceBasis)
            .filter(x -> maxPrice == null || x.result().unitPrice().compareTo(maxPrice) <= 0)
            .toList();

        Stream<Priced> sorted = priced.stream();
        if ("price".equals(sort)) {
            sorted = sorted.sorted(Comparator.comparing((Priced x) -> x.result().unitPrice()));
        } else if ("rating".equals(sort)) {
            sorted = sorted.sorted(Comparator.comparing((Priced x) -> x.result().rating(),
                Comparator.nullsLast(Comparator.reverseOrder())));
        }
        List<SearchResult> results = sorted.limit(limit).map(Priced::result).toList();

        RateInfo rateInfo = priced.stream().map(Priced::rate).filter(r -> r != null).findFirst().orElse(null);
        return new SearchResponse(type.name(), tripId, trip.getDestination(), currency.name(),
            trip.getNumberOfTravelers(), provider.name(), provider.live(), provider.live() ? null : DEMO_NOTICE,
            rateInfo, results);
    }

    // ---------- Pricing (also used when adding an item) ----------

    // A result plus the rate used to price it (null if no conversion was needed)
    record Priced(SearchResult result, RateInfo rate) {}

    // Converts the unit price to the trip's currency, then total = unit x quantity,
    // so the numbers shown always multiply out exactly.
    Priced price(ProviderItem item, SupportedCurrency currency, ExchangeRates rates, boolean alreadyAdded) {
        BigDecimal unit;
        RateInfo rate = null;
        if (item.priceCurrency() == currency) {
            unit = item.unitPrice().setScale(currency.getDecimalPlaces(), RoundingMode.HALF_UP);
        } else {
            ConversionResponse converted = currencyService.convert(item.unitPrice(), item.priceCurrency(), currency, rates);
            unit = converted.convertedAmount();
            rate = RateInfo.of(converted);
        }
        BigDecimal quantity = BigDecimal.valueOf(item.quantity());
        BigDecimal originalTotal = item.unitPrice().multiply(quantity);

        SearchResult result = new SearchResult(
            item.itemId(), item.type().name(), item.name(), item.location(), item.rating(),
            item.date(), item.endDate(), item.startTime(), item.endTime(),
            item.returnStartTime(), item.returnEndTime(),
            item.priceBasis().name(), item.priceBasis().getLabel(),
            unit, item.quantity(), item.quantityDescription(), unit.multiply(quantity), currency.name(),
            item.unitPrice(), originalTotal, item.priceCurrency().name(),
            item.type().getBudgetCategory(), alreadyAdded, provider.name(), item.details());
        return new Priced(result, rate);
    }

    // ---------- Building and checking the search ----------

    private SearchCriteria criteria(TripItemType type, SearchParams p, Trip trip) {
        int travelers = Math.max(1, trip.getNumberOfTravelers());
        if (travelers > 99) {
            throw new IllegalArgumentException("Search supports trips with up to 99 travelers");
        }
        BigDecimal minRating = parseMinRating(p.minRating());

        return switch (type) {
            case FLIGHT -> {
                String origin = text(p.origin(), "origin");
                if (origin == null || !origin.matches("[A-Za-z]{3}")) {
                    throw new IllegalArgumentException("origin is required: a 3-letter airport code like ORD");
                }
                LocalDate depart = dateOr(p.departDate(), "departDate", trip.getStartDate());
                LocalDate ret = dateOr(p.returnDate(), "returnDate", trip.getEndDate());
                inTrip(depart, "departDate", trip);
                inTrip(ret, "returnDate", trip);
                if (ret.isBefore(depart)) {
                    throw new IllegalArgumentException("returnDate can't be before departDate");
                }
                yield new SearchCriteria(type, trip.getDestination(), depart, ret, travelers,
                    origin.toUpperCase(Locale.ROOT), null, null, null, null);
            }
            case HOTEL -> {
                LocalDate checkIn = dateOr(p.checkIn(), "checkIn", trip.getStartDate());
                LocalDate checkOut = dateOr(p.checkOut(), "checkOut", trip.getEndDate());
                inTrip(checkIn, "checkIn", trip);
                inTrip(checkOut, "checkOut", trip);
                if (!checkOut.isAfter(checkIn)) {
                    throw new IllegalArgumentException("checkOut must be after checkIn (at least one night)");
                }
                yield new SearchCriteria(type, trip.getDestination(), checkIn, checkOut, travelers,
                    null, null, null, null, minRating);
            }
            case RESTAURANT -> {
                LocalDate date = dateOr(p.date(), "date", trip.getStartDate());
                inTrip(date, "date", trip);
                yield new SearchCriteria(type, trip.getDestination(), date, null, travelers,
                    null, text(p.cuisine(), "cuisine"), parsePriceLevel(p.priceLevel()), null, minRating);
            }
            case ACTIVITY -> {
                LocalDate date = dateOr(p.date(), "date", trip.getStartDate());
                inTrip(date, "date", trip);
                yield new SearchCriteria(type, trip.getDestination(), date, null, travelers,
                    null, null, null, text(p.keyword(), "keyword"), minRating);
            }
        };
    }

    private static void inTrip(LocalDate date, String name, Trip trip) {
        if (date.isBefore(trip.getStartDate()) || date.isAfter(trip.getEndDate())) {
            throw new IllegalArgumentException(name + " must be within the trip dates ("
                + trip.getStartDate() + " to " + trip.getEndDate() + ")");
        }
    }

    private static LocalDate dateOr(String value, String name, LocalDate fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(name + " must be a date like 2027-06-01");
        }
    }

    // Optional free text: trimmed, at most 50 characters, null when blank
    private static String text(String value, String name) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > MAX_TEXT) {
            throw new IllegalArgumentException(name + " can be at most " + MAX_TEXT + " characters");
        }
        return trimmed;
    }

    private static BigDecimal parseMaxPrice(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        BigDecimal price = number(value, "maxPrice");
        if (price.signum() <= 0 || price.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("maxPrice must be greater than 0 with at most 2 decimal places");
        }
        return price;
    }

    private static BigDecimal parseMinRating(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        BigDecimal rating = number(value, "minRating");
        if (rating.compareTo(BigDecimal.ZERO) < 0 || rating.compareTo(BigDecimal.valueOf(5)) > 0) {
            throw new IllegalArgumentException("minRating must be between 0 and 5");
        }
        return rating;
    }

    private static Integer parsePriceLevel(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        int level = whole(value, "priceLevel");
        if (level < 1 || level > 4) {
            throw new IllegalArgumentException("priceLevel must be 1, 2, 3 or 4");
        }
        return level;
    }

    private static int parseLimit(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_LIMIT;
        }
        int limit = whole(value, "limit");
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT);
        }
        return limit;
    }

    private static String parseSort(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String sort = value.trim().toLowerCase(Locale.ROOT);
        if (!sort.equals("price") && !sort.equals("rating")) {
            throw new IllegalArgumentException("sort must be price or rating");
        }
        return sort;
    }

    private static BigDecimal number(String value, String name) {
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " must be a number");
        }
    }

    private static int whole(String value, String name) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " must be a whole number");
        }
    }
}
