package com.tripbudgetplanner.backend.search;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.tripbudgetplanner.backend.currency.SupportedCurrency;

// DEMO DATA, NOT LIVE PRICES OR AVAILABILITY.
// Makes realistic-looking but invented flights, hotels, restaurants and activities.
// Names are made up and end in "(demo)"; airlines are placeholders like "Demo Air".
// Prices are in USD and depend on how expensive the destination is.
//
// Results are repeatable: the same search always returns the same items, and each item ID
// contains the search it came from, e.g. demo-hotel-tokyo-20270601-20270610-3-x-2
//   (type, destination, start date, end date or x, travelers, origin airport or x, position).
// findItem rebuilds the item from its ID, so a changed ID can't invent a price:
// it either matches a real search for some trip, or it is rejected later by TripItemService.
@Component
@ConditionalOnProperty(name = "app.search.provider", havingValue = "mock", matchIfMissing = true)
public class MockTravelSearchProvider implements TravelSearchProvider {

    static final String NAME = "demo";
    private static final SupportedCurrency PRICE_CURRENCY = SupportedCurrency.USD;
    private static final DateTimeFormatter ID_DATE = DateTimeFormatter.BASIC_ISO_DATE;   // 20270601

    private static final int FLIGHT_COUNT = 6;
    private static final int HOTEL_COUNT = 8;
    private static final int RESTAURANT_COUNT = 10;
    private static final int ACTIVITY_COUNT = 10;

    private static final Pattern ID_PATTERN = Pattern.compile(
        "demo-(flight|hotel|restaurant|activity)-([a-z0-9_]{1,40})-(\\d{8})-(\\d{8}|x)-(\\d{1,2})-([a-z]{3}|x)-(\\d{1,2})");

    // ---------- Destination profiles ----------

    // How expensive a place is (1.0 = average), its main airport, areas and food
    private record City(String name, double costLevel, int flightUsd, String airport,
                        List<String> areas, List<String> cuisines) {}

    // Matched with "contains" against the destination
    private static final Map<String, City> CITIES = new LinkedHashMap<>();

    static {
        City japan = new City("Tokyo", 1.25, 1300, "HND",
            List.of("Shinjuku", "Shibuya", "Asakusa", "Ginza"), List.of("Sushi", "Ramen", "Izakaya", "Tempura"));
        CITIES.put("tokyo", japan);
        CITIES.put("kyoto", new City("Kyoto", 1.15, 1300, "KIX",
            List.of("Gion", "Higashiyama", "Arashiyama", "Downtown"), List.of("Kaiseki", "Ramen", "Tofu", "Matcha cafe")));
        CITIES.put("osaka", new City("Osaka", 1.05, 1250, "KIX",
            List.of("Namba", "Umeda", "Shinsekai", "Tennoji"), List.of("Okonomiyaki", "Takoyaki", "Ramen", "Izakaya")));
        CITIES.put("paris", new City("Paris", 1.3, 950, "CDG",
            List.of("Le Marais", "Montmartre", "Saint-Germain", "Latin Quarter"), List.of("French", "Bistro", "Bakery", "Wine bar")));
        CITIES.put("london", new City("London", 1.4, 900, "LHR",
            List.of("Covent Garden", "South Bank", "Camden", "Kensington"), List.of("British", "Indian", "Pub", "Seafood")));
        CITIES.put("rome", new City("Rome", 1.1, 950, "FCO",
            List.of("Trastevere", "Monti", "Centro Storico", "Prati"), List.of("Italian", "Pizza", "Gelato", "Trattoria")));
        CITIES.put("barcelona", new City("Barcelona", 1.0, 900, "BCN",
            List.of("Gothic Quarter", "Eixample", "El Born", "Barceloneta"), List.of("Tapas", "Catalan", "Seafood", "Paella")));
        CITIES.put("new_york", new City("New York", 1.5, 350, "JFK",
            List.of("Midtown", "SoHo", "Brooklyn", "Upper West Side"), List.of("Pizza", "Deli", "Steakhouse", "Bagels")));
        CITIES.put("cancun", new City("Cancun", 0.85, 450, "CUN",
            List.of("Hotel Zone", "Downtown", "Puerto Juarez", "Playa Norte"), List.of("Mexican", "Tacos", "Seafood", "Yucatecan")));
        CITIES.put("bangkok", new City("Bangkok", 0.55, 1100, "BKK",
            List.of("Sukhumvit", "Silom", "Old Town", "Riverside"), List.of("Thai", "Street food", "Noodles", "Curry")));
        CITIES.put("bali", new City("Bali", 0.55, 1250, "DPS",
            List.of("Ubud", "Seminyak", "Canggu", "Uluwatu"), List.of("Indonesian", "Seafood", "Vegan", "Cafe")));
        CITIES.put("seoul", new City("Seoul", 0.95, 1200, "ICN",
            List.of("Myeongdong", "Hongdae", "Gangnam", "Insadong"), List.of("Korean BBQ", "Bibimbap", "Street food", "Fried chicken")));
        CITIES.put("sydney", new City("Sydney", 1.2, 1500, "SYD",
            List.of("The Rocks", "Darling Harbour", "Bondi", "Surry Hills"), List.of("Modern Australian", "Seafood", "Cafe", "Thai")));
        CITIES.put("toronto", new City("Toronto", 1.0, 400, "YYZ",
            List.of("Downtown", "Kensington Market", "Distillery District", "Yorkville"), List.of("Canadian", "Dim sum", "Poutine", "Italian")));
        CITIES.put("delhi", new City("Delhi", 0.45, 1150, "DEL",
            List.of("Connaught Place", "Old Delhi", "Hauz Khas", "Karol Bagh"), List.of("North Indian", "Street food", "Mughlai", "Vegetarian")));
    }

    private static final List<String> CARRIERS = List.of("Demo Air", "Sample Airways", "Example Jet", "Placeholder Air");
    private static final List<String> NAME_START = List.of("Lantern", "Harbor", "Maple", "Cobalt", "Juniper",
        "Willow", "Summit", "Atlas", "Copper", "Linden", "Saffron", "Indigo");
    private static final List<String> HOTEL_END = List.of("House", "Inn", "Suites", "Hotel", "Residences", "Lodge");
    private static final List<String> RESTAURANT_END = List.of("Kitchen", "Table", "Bistro", "Corner", "Garden", "Counter");
    private static final List<String> ACTIVITY_KINDS = List.of("Walking tour", "Food tour", "Museum pass", "Day trip",
        "Cooking class", "Bike tour", "River cruise", "Night market tour", "Private guide (half day)", "Sunset viewpoint tour");

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean live() {
        return false;
    }

    // ---------- Search ----------

    @Override
    public List<ProviderItem> search(SearchCriteria c) {
        String dest = destinationKey(c.destination());
        String origin = c.origin() == null ? "x" : c.origin().toLowerCase(Locale.ROOT);
        LocalDate end = c.type() == TripItemType.FLIGHT || c.type() == TripItemType.HOTEL ? c.endDate() : null;

        List<ProviderItem> all = generate(c.type(), dest, c.startDate(), end, c.travelers(), origin);
        return all.stream().filter(item -> matchesFilters(item, c)).toList();
    }

    @Override
    public Optional<ProviderItem> findItem(String itemId) {
        if (itemId == null || itemId.length() > 120) {
            return Optional.empty();
        }
        Matcher m = ID_PATTERN.matcher(itemId);
        if (!m.matches()) {
            return Optional.empty();
        }
        try {
            TripItemType type = TripItemType.valueOf(m.group(1).toUpperCase(Locale.ROOT));
            String dest = m.group(2);
            LocalDate start = LocalDate.parse(m.group(3), ID_DATE);
            LocalDate end = "x".equals(m.group(4)) ? null : LocalDate.parse(m.group(4), ID_DATE);
            int travelers = Integer.parseInt(m.group(5));
            String origin = m.group(6);
            int index = Integer.parseInt(m.group(7));

            // The ID's parts must fit the type, just like a real search would produce
            boolean needsEnd = type == TripItemType.FLIGHT || type == TripItemType.HOTEL;
            if (needsEnd != (end != null) || (type == TripItemType.FLIGHT) == "x".equals(origin)
                    || travelers < 1 || travelers > 99) {
                return Optional.empty();
            }
            if (type == TripItemType.FLIGHT && end.isBefore(start)) {
                return Optional.empty();
            }
            if (type == TripItemType.HOTEL && !end.isAfter(start)) {
                return Optional.empty();
            }

            List<ProviderItem> all = generate(type, dest, start, end, travelers, origin);
            if (index >= all.size()) {
                return Optional.empty();
            }
            ProviderItem item = all.get(index);
            // Only accept the exact ID this provider would have made (no leading zeros etc.)
            return item.itemId().equals(itemId) ? Optional.of(item) : Optional.empty();
        } catch (DateTimeParseException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    // "Cancún, Mexico" -> "cancun_mexico" (lowercase letters, digits and _ only)
    @Override
    public String destinationKey(String destination) {
        String plain = Normalizer.normalize(destination == null ? "" : destination, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "_")
            .replaceAll("^_+|_+$", "");
        if (plain.isEmpty()) {
            return "trip";
        }
        return plain.length() > 40 ? plain.substring(0, 40) : plain;
    }

    // ---------- Generating the demo items ----------

    private List<ProviderItem> generate(TripItemType type, String dest, LocalDate start, LocalDate end,
                                        int travelers, String origin) {
        City city = cityFor(dest);
        // Same search -> same seed -> same items. Travelers only change quantities, not prices.
        Random random = new Random((type + "|" + dest + "|" + start + "|" + end + "|" + origin).hashCode());
        String idBase = "demo-" + type.name().toLowerCase(Locale.ROOT) + "-" + dest + "-" + start.format(ID_DATE)
            + "-" + (end == null ? "x" : end.format(ID_DATE)) + "-" + travelers + "-" + origin + "-";

        List<ProviderItem> items = new ArrayList<>();
        switch (type) {
            case FLIGHT -> {
                for (int i = 0; i < FLIGHT_COUNT; i++) {
                    items.add(flight(idBase + i, city, dest, start, end, travelers, origin, random));
                }
            }
            case HOTEL -> {
                for (int i = 0; i < HOTEL_COUNT; i++) {
                    items.add(hotel(idBase + i, city, dest, start, end, travelers, random));
                }
            }
            case RESTAURANT -> {
                for (int i = 0; i < RESTAURANT_COUNT; i++) {
                    items.add(restaurant(idBase + i, city, dest, start, travelers, random));
                }
            }
            case ACTIVITY -> {
                for (int i = 0; i < ACTIVITY_KINDS.size() && i < ACTIVITY_COUNT; i++) {
                    items.add(activity(idBase + i, city, dest, start, travelers, ACTIVITY_KINDS.get(i), random));
                }
            }
        }
        return items;
    }

    private ProviderItem flight(String id, City city, String dest, LocalDate depart, LocalDate ret,
                                int travelers, String origin, Random r) {
        String carrier = pick(CARRIERS, r);
        String code = carrier.substring(0, 1) + carrier.split(" ")[1].charAt(0);
        int stops = r.nextInt(3) == 0 ? 1 : 0;
        int hours = Math.max(2, (int) Math.round(city.flightUsd() / 110.0)) + stops * 2;

        LocalTime outDepart = LocalTime.of(6 + r.nextInt(15), r.nextBoolean() ? 0 : 30);
        LocalTime backDepart = LocalTime.of(7 + r.nextInt(14), r.nextBoolean() ? 15 : 45);

        double factor = 0.8 + r.nextDouble() * 0.55 - stops * 0.12;
        BigDecimal perPerson = usd(city.flightUsd() * factor);

        Map<String, String> details = new LinkedHashMap<>();
        details.put("airline", carrier);
        details.put("outboundFlight", code + (100 + r.nextInt(900)));
        details.put("returnFlight", code + (100 + r.nextInt(900)));
        details.put("from", origin.toUpperCase(Locale.ROOT));
        details.put("to", city.airport());
        details.put("stops", String.valueOf(stops));
        details.put("durationHours", String.valueOf(hours));
        details.put("cabin", "Economy");

        return new ProviderItem(id, TripItemType.FLIGHT,
            carrier + " " + origin.toUpperCase(Locale.ROOT) + " → " + city.airport() + " round trip (demo)",
            origin.toUpperCase(Locale.ROOT) + " → " + city.airport(),
            null, depart, ret,
            outDepart, sameDayArrival(outDepart, hours),
            backDepart, sameDayArrival(backDepart, hours),
            PriceBasis.PER_PERSON_ROUND_TRIP, perPerson, PRICE_CURRENCY, travelers,
            travelers + (travelers == 1 ? " traveler" : " travelers"),
            dest, travelers, details);
    }

    private ProviderItem hotel(String id, City city, String dest, LocalDate checkIn, LocalDate checkOut,
                               int travelers, Random r) {
        int stars = 2 + r.nextInt(4);
        BigDecimal nightly = usd((45 + stars * 35 + r.nextInt(41)) * city.costLevel());
        int nights = (int) ChronoUnit.DAYS.between(checkIn, checkOut);
        int rooms = (travelers + 1) / 2;   // up to 2 people per room

        Map<String, String> details = new LinkedHashMap<>();
        details.put("stars", String.valueOf(stars));
        details.put("nights", String.valueOf(nights));
        details.put("rooms", String.valueOf(rooms));
        details.put("checkOut", checkOut.toString());
        details.put("breakfastIncluded", String.valueOf(r.nextBoolean()));

        return new ProviderItem(id, TripItemType.HOTEL,
            pick(NAME_START, r) + " " + pick(HOTEL_END, r) + " " + city.name() + " (demo)",
            pick(city.areas(), r) + ", " + city.name(),
            rating(r), checkIn, checkOut, LocalTime.of(15, 0), null, null, null,
            PriceBasis.PER_ROOM_PER_NIGHT, nightly, PRICE_CURRENCY, rooms * nights,
            rooms + (rooms == 1 ? " room x " : " rooms x ") + nights + (nights == 1 ? " night" : " nights"),
            dest, travelers, details);
    }

    private ProviderItem restaurant(String id, City city, String dest, LocalDate date, int travelers, Random r) {
        int level = 1 + r.nextInt(4);
        String cuisine = pick(city.cuisines(), r);
        BigDecimal perPerson = usd((10 + level * 14 + r.nextInt(11)) * city.costLevel());
        boolean dinner = r.nextInt(3) > 0;
        LocalTime start = dinner ? LocalTime.of(18 + r.nextInt(3), r.nextBoolean() ? 0 : 30) : LocalTime.of(12 + r.nextInt(2), 0);

        Map<String, String> details = new LinkedHashMap<>();
        details.put("cuisine", cuisine);
        details.put("priceLevel", "$".repeat(level));
        details.put("meal", dinner ? "Dinner" : "Lunch");

        return new ProviderItem(id, TripItemType.RESTAURANT,
            pick(NAME_START, r) + " " + pick(RESTAURANT_END, r) + " (demo)",
            pick(city.areas(), r) + ", " + city.name(),
            rating(r), date, null, start, start.plusMinutes(90), null, null,
            PriceBasis.PER_PERSON, perPerson, PRICE_CURRENCY, travelers,
            travelers + (travelers == 1 ? " person" : " people"),
            dest, travelers, details);
    }

    private ProviderItem activity(String id, City city, String dest, LocalDate date, int travelers,
                                  String kind, Random r) {
        boolean privateGuide = kind.startsWith("Private");
        BigDecimal price = privateGuide
            ? usd((180 + r.nextInt(201)) * city.costLevel())
            : usd((18 + r.nextInt(93)) * city.costLevel());
        int hours = 2 + r.nextInt(5);
        LocalTime start = LocalTime.of(9 + r.nextInt(8), r.nextBoolean() ? 0 : 30);

        Map<String, String> details = new LinkedHashMap<>();
        details.put("kind", kind);
        details.put("durationHours", String.valueOf(hours));

        return new ProviderItem(id, TripItemType.ACTIVITY,
            city.name() + " " + kind.toLowerCase(Locale.ROOT) + " (demo)",
            pick(city.areas(), r) + ", " + city.name(),
            rating(r), date, null, start, start.plusHours(hours), null, null,
            privateGuide ? PriceBasis.PER_GROUP : PriceBasis.PER_PERSON, price, PRICE_CURRENCY,
            privateGuide ? 1 : travelers,
            privateGuide ? "whole group" : travelers + (travelers == 1 ? " person" : " people"),
            dest, travelers, details);
    }

    // ---------- Helpers ----------

    private static boolean matchesFilters(ProviderItem item, SearchCriteria c) {
        if (c.minRating() != null && (item.rating() == null || item.rating().compareTo(c.minRating()) < 0)) {
            return false;
        }
        if (c.cuisine() != null && !item.details().getOrDefault("cuisine", "")
                .toLowerCase(Locale.ROOT).contains(c.cuisine().toLowerCase(Locale.ROOT))) {
            return false;
        }
        if (c.priceLevel() != null && !("$".repeat(c.priceLevel())).equals(item.details().get("priceLevel"))) {
            return false;
        }
        return c.keyword() == null
            || item.name().toLowerCase(Locale.ROOT).contains(c.keyword().toLowerCase(Locale.ROOT));
    }

    private static City cityFor(String dest) {
        for (Map.Entry<String, City> entry : CITIES.entrySet()) {
            if (dest.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        // Any other destination: average prices and generic area names
        String name = Character.toUpperCase(dest.charAt(0)) + dest.substring(1).replace('_', ' ');
        if (name.length() > 30) {
            name = name.substring(0, 30).trim();
        }
        return new City(name, 1.0, 700, "DST", List.of("City Center", "Old Town", "Waterfront", "Market District"),
            List.of("Local", "International", "Cafe", "Seafood"));
    }

    // Returns the arrival time, or null when the flight lands the next day
    private static LocalTime sameDayArrival(LocalTime depart, int hours) {
        int minutes = depart.getHour() * 60 + depart.getMinute() + hours * 60;
        return minutes < 24 * 60 ? depart.plusHours(hours) : null;
    }

    private static BigDecimal rating(Random r) {
        return BigDecimal.valueOf(34 + r.nextInt(16)).movePointLeft(1);   // 3.4 - 4.9
    }

    private static BigDecimal usd(double amount) {
        return BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP);
    }

    private static <T> T pick(List<T> list, Random r) {
        return list.get(r.nextInt(list.size()));
    }
}
