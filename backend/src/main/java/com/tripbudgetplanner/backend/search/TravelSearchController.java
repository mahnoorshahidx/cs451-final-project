package com.tripbudgetplanner.backend.search;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tripbudgetplanner.backend.search.TravelSearchService.SearchParams;
import com.tripbudgetplanner.backend.search.dto.AddTripItemRequest;
import com.tripbudgetplanner.backend.search.dto.AddTripItemResponse;
import com.tripbudgetplanner.backend.search.dto.SearchProviderResponse;
import com.tripbudgetplanner.backend.search.dto.SearchResponse;

import jakarta.validation.Valid;

// UC6: search for travel items and add them to a trip.
// Query parameters are read as text and checked in TravelSearchService,
// so every mistake gets the usual { "message": "..." } 400 response.
@RestController
public class TravelSearchController {

    private final TravelSearchService searchService;
    private final TripItemService itemService;

    public TravelSearchController(TravelSearchService searchService, TripItemService itemService) {
        this.searchService = searchService;
        this.itemService = itemService;
    }

    // Which provider is active ("demo" for now)
    @GetMapping("/api/search/provider")
    public SearchProviderResponse provider() {
        return searchService.providerInfo();
    }

    @GetMapping("/api/trips/{tripId}/search/flights")
    public SearchResponse flights(@PathVariable Long tripId,
                                  @RequestParam(required = false) String origin,
                                  @RequestParam(required = false) String departDate,
                                  @RequestParam(required = false) String returnDate,
                                  @RequestParam(required = false) String maxPrice,
                                  @RequestParam(required = false) String sort,
                                  @RequestParam(required = false) String limit,
                                  @AuthenticationPrincipal Long userId) {
        return searchService.search(tripId, TripItemType.FLIGHT, new SearchParams(origin, departDate, returnDate,
            null, null, null, null, null, null, null, maxPrice, sort, limit), userId);
    }

    @GetMapping("/api/trips/{tripId}/search/hotels")
    public SearchResponse hotels(@PathVariable Long tripId,
                                 @RequestParam(required = false) String checkIn,
                                 @RequestParam(required = false) String checkOut,
                                 @RequestParam(required = false) String minRating,
                                 @RequestParam(required = false) String maxPrice,
                                 @RequestParam(required = false) String sort,
                                 @RequestParam(required = false) String limit,
                                 @AuthenticationPrincipal Long userId) {
        return searchService.search(tripId, TripItemType.HOTEL, new SearchParams(null, null, null,
            checkIn, checkOut, null, null, null, null, minRating, maxPrice, sort, limit), userId);
    }

    @GetMapping("/api/trips/{tripId}/search/restaurants")
    public SearchResponse restaurants(@PathVariable Long tripId,
                                      @RequestParam(required = false) String date,
                                      @RequestParam(required = false) String cuisine,
                                      @RequestParam(required = false) String priceLevel,
                                      @RequestParam(required = false) String minRating,
                                      @RequestParam(required = false) String maxPrice,
                                      @RequestParam(required = false) String sort,
                                      @RequestParam(required = false) String limit,
                                      @AuthenticationPrincipal Long userId) {
        return searchService.search(tripId, TripItemType.RESTAURANT, new SearchParams(null, null, null,
            null, null, date, cuisine, priceLevel, null, minRating, maxPrice, sort, limit), userId);
    }

    @GetMapping("/api/trips/{tripId}/search/activities")
    public SearchResponse activities(@PathVariable Long tripId,
                                     @RequestParam(required = false) String date,
                                     @RequestParam(required = false) String keyword,
                                     @RequestParam(required = false) String minRating,
                                     @RequestParam(required = false) String maxPrice,
                                     @RequestParam(required = false) String sort,
                                     @RequestParam(required = false) String limit,
                                     @AuthenticationPrincipal Long userId) {
        return searchService.search(tripId, TripItemType.ACTIVITY, new SearchParams(null, null, null,
            null, null, date, null, null, keyword, minRating, maxPrice, sort, limit), userId);
    }

    // Owner or editor: add a result to the itinerary (and to the budget with updateBudget=true)
    @PostMapping("/api/trips/{tripId}/search/items")
    public ResponseEntity<AddTripItemResponse> addItem(@PathVariable Long tripId,
                                                       @Valid @RequestBody AddTripItemRequest request,
                                                       @AuthenticationPrincipal Long userId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(itemService.addItem(tripId, request, userId));
    }
}
