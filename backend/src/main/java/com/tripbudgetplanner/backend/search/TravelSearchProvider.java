package com.tripbudgetplanner.backend.search;

import java.util.List;
import java.util.Optional;

// A source of travel search results (UC6).
// Today: MockTravelSearchProvider (demo data). To use a real travel API later, add another
// implementation and select it with app.search.provider. Keys go in environment variables.
public interface TravelSearchProvider {

    // Short name stored with added items, e.g. "demo"
    String name();

    // False for demo data; true for a real API
    boolean live();

    // Results for the criteria (non-price filters applied), at most ~20
    List<ProviderItem> search(SearchCriteria criteria);

    // Looks up a result again by its ID (so prices come from the provider, never the client).
    // Empty if the ID is unknown, malformed or no longer available.
    Optional<ProviderItem> findItem(String itemId);

    // How this provider names a destination, used to check an item belongs to the trip
    String destinationKey(String destination);
}
