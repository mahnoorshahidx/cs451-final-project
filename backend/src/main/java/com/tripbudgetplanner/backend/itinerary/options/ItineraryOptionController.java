package com.tripbudgetplanner.backend.itinerary.options;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tripbudgetplanner.backend.itinerary.dto.ItineraryActivityResponse;
import com.tripbudgetplanner.backend.itinerary.dto.ItineraryGenerationRequest;

import jakarta.validation.Valid;

// UC5: AI itinerary options to compare before choosing one
@RestController
@RequestMapping("/api/trips/{tripId}/itinerary-options")
public class ItineraryOptionController {

    private final ItineraryOptionService optionService;

    public ItineraryOptionController(ItineraryOptionService optionService) {
        this.optionService = optionService;
    }

    // Make new options (replaces older drafts). Same body as POST /activities/generate.
    @PostMapping
    public ResponseEntity<List<ItineraryOptionResponse>> generate(
            @PathVariable Long tripId,
            @Valid @RequestBody ItineraryGenerationRequest request,
            @AuthenticationPrincipal Long userId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(optionService.generate(tripId, request, userId));
    }

    @GetMapping
    public List<ItineraryOptionResponse> list(@PathVariable Long tripId, @AuthenticationPrincipal Long userId) {
        return optionService.list(tripId, userId);
    }

    // Keep one option: its activities are added to the itinerary and the drafts are cleared
    @PostMapping("/{optionId}/apply")
    public ResponseEntity<List<ItineraryActivityResponse>> apply(
            @PathVariable Long tripId,
            @PathVariable Long optionId,
            @RequestParam(defaultValue = "false") boolean replaceExisting,
            @AuthenticationPrincipal Long userId) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(optionService.apply(tripId, optionId, replaceExisting, userId));
    }

    @DeleteMapping
    public ResponseEntity<Void> discard(@PathVariable Long tripId, @AuthenticationPrincipal Long userId) {
        optionService.discard(tripId, userId);
        return ResponseEntity.noContent().build();
    }
}
