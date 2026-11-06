
package com.tripbudgetplanner.backend.photo;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@RestController
@RequestMapping("/api/destination-photo")
public class DestinationPhotoController {

    private final RestClient client = RestClient.create();

    // Reuse successful results to reduce Unsplash API requests.
    private final Map<String, PhotoResponse> cache =
            new ConcurrentHashMap<>();

    @Value("${UNSPLASH_ACCESS_KEY:}")
    private String accessKey;

    @GetMapping
    public ResponseEntity<PhotoResponse> getPhoto(
            @RequestParam("destination") String destination) {

        String location = destination.trim();

        if (location.isBlank() || location.length() > 200) {
            return ResponseEntity.badRequest().build();
        }

        if (accessKey == null || accessKey.isBlank()) {
            return ResponseEntity
                    .status(HttpStatus.SERVICE_UNAVAILABLE)
                    .build();
        }

        String cacheKey = location.toLowerCase(Locale.ROOT);

        if (cache.containsKey(cacheKey)) {
            return ResponseEntity.ok(cache.get(cacheKey));
        }

        String search = location + " scenic architecture landscape";

        String url = "https://api.unsplash.com/search/photos"
                + "?query="
                + URLEncoder.encode(search, StandardCharsets.UTF_8)
                + "&orientation=landscape"
                + "&content_filter=high"
                + "&per_page=1";

        try {
            UnsplashResult result = client.get()
                    .uri(URI.create(url))
                    .header("Authorization", "Client-ID " + accessKey)
                    .header("Accept-Version", "v1")
                    .retrieve()
                    .body(UnsplashResult.class);

            if (result == null || result.results() == null
                    || result.results().isEmpty()) {
                return ResponseEntity.noContent().build();
            }

            UnsplashPhoto photo = result.results().get(0);

            if (photo.urls() == null || photo.user() == null
                    || photo.user().links() == null
                    || photo.urls().regular() == null
                    || photo.user().links().html() == null) {
                return ResponseEntity.noContent().build();
            }

            String profileUrl = photo.user().links().html();

            String separator = profileUrl.contains("?") ? "&" : "?";

            profileUrl += separator
                    + "utm_source=trip_budget_planner"
                    + "&utm_medium=referral";

            PhotoResponse response = new PhotoResponse(
                    photo.urls().regular(),
                    photo.user().name(),
                    profileUrl,
                    "https://unsplash.com/"
                            + "?utm_source=trip_budget_planner"
                            + "&utm_medium=referral"
            );

            cache.put(cacheKey, response);

            return ResponseEntity.ok(response);

        } catch (RestClientException exception) {
            return ResponseEntity
                    .status(HttpStatus.BAD_GATEWAY)
                    .build();
        }
    }

    public record PhotoResponse(
            String imageUrl,
            String photographer,
            String photographerUrl,
            String unsplashUrl
    ) {}

    public record UnsplashResult(
            List<UnsplashPhoto> results
    ) {}

    public record UnsplashPhoto(
            UnsplashUrls urls,
            UnsplashUser user
    ) {}

    public record UnsplashUrls(
            String regular
    ) {}

    public record UnsplashUser(
            String name,
            UnsplashLinks links
    ) {}

    public record UnsplashLinks(
            String html
    ) {}
}
