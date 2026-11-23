package com.tripbudgetplanner.backend.currency;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.tripbudgetplanner.backend.currency.exception.ExchangeRateException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

// Live exchange rates from Frankfurter (https://frankfurter.dev).
// Frankfurter is free, open source and needs no API key, so there is no secret to store.
// Uses Java's built-in HttpClient (like AnthropicClient), so no extra Maven dependency is needed.
//
// Request:  GET {base-url}/v1/latest?base=USD&symbols=EUR,GBP,JPY,CAD,AUD,INR,KRW
// Reply:    { "amount": 1.0, "base": "USD", "date": "2026-11-20", "rates": { "EUR": 0.95, ... } }
@Component
public class FrankfurterExchangeRateProvider implements ExchangeRateProvider {

    private final ObjectMapper mapper;
    private final HttpClient http;
    private final URI endpoint;
    private final Duration timeout;

    public FrankfurterExchangeRateProvider(
        ObjectMapper mapper,
        @Value("${app.fx.base-url:https://api.frankfurter.dev}") String baseUrl,
        @Value("${app.fx.timeout-seconds:5}") int timeoutSeconds
    ) {
        this.mapper = mapper;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.http = HttpClient.newBuilder().connectTimeout(this.timeout).build();

        // Ask only for the currencies we support (everything except the USD base)
        String symbols = Arrays.stream(SupportedCurrency.values())
            .filter(c -> c != SupportedCurrency.USD)
            .map(Enum::name)
            .collect(Collectors.joining(","));
        this.endpoint = URI.create(baseUrl.replaceAll("/+$", "") + "/v1/latest?base=USD&symbols=" + symbols);
    }

    @Override
    public ExchangeRates fetchRates() {
        HttpRequest request = HttpRequest.newBuilder(endpoint)
            .timeout(timeout)
            .header("accept", "application/json")
            .GET()
            .build();

        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new ExchangeRateException("Exchange rate request failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExchangeRateException("Exchange rate request was interrupted", e);
        }

        if (response.statusCode() != 200) {
            throw new ExchangeRateException("Exchange rate API returned HTTP " + response.statusCode());
        }
        return parse(response.body());
    }

    // Reads Frankfurter's JSON reply. Package-private so the unit test can call it directly.
    ExchangeRates parse(String json) {
        JsonNode root;
        try {
            root = mapper.readTree(json);
        } catch (JacksonException e) {
            throw new ExchangeRateException("Exchange rate reply was not valid JSON", e);
        }

        if (!"USD".equals(text(root.path("base")))) {
            throw new ExchangeRateException("Exchange rate reply did not use USD as the base");
        }

        Map<SupportedCurrency, BigDecimal> rates = new EnumMap<>(SupportedCurrency.class);
        rates.put(SupportedCurrency.USD, BigDecimal.ONE);

        JsonNode ratesNode = root.path("rates");
        for (SupportedCurrency currency : SupportedCurrency.values()) {
            if (currency == SupportedCurrency.USD) {
                continue;
            }
            JsonNode value = ratesNode.path(currency.name());
            if (!value.isNumber()) {
                throw new ExchangeRateException("Exchange rate reply is missing " + currency);
            }
            // toString() gives the number exactly as written in the JSON, e.g. "0.9512"
            rates.put(currency, new BigDecimal(value.toString()));
        }

        // ExchangeRates also checks that every rate is present and positive
        return new ExchangeRates(rates, parseDate(text(root.path("date"))), ExchangeRates.LIVE);
    }

    // The publish date, or null if it's missing or not a valid date
    private static LocalDate parseDate(String text) {
        try {
            return text.isEmpty() ? null : LocalDate.parse(text);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    // Field as text; "" when missing or null
    private static String text(JsonNode node) {
        return node.isMissingNode() || node.isNull() ? "" : node.asString();
    }
}
