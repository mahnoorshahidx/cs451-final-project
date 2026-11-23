package com.tripbudgetplanner.backend.currency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;
import com.tripbudgetplanner.backend.currency.exception.ExchangeRateException;

import tools.jackson.databind.ObjectMapper;

class FrankfurterExchangeRateProviderTest {

    private static final String VALID_REPLY = """
        {"amount":1.0,"base":"USD","date":"2026-11-20",
         "rates":{"AUD":1.5512,"CAD":1.4021,"EUR":0.9512,"GBP":0.7834,
                  "INR":84.52,"JPY":151.23,"KRW":1398.4}}
        """;

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    // ---------- Reading the JSON reply ----------

    @Test
    void parsesAValidReply() {
        ExchangeRates rates = provider("https://example.invalid").parse(VALID_REPLY);

        assertEquals(new BigDecimal("0.9512"), rates.rateFor(SupportedCurrency.EUR));
        assertEquals(new BigDecimal("1398.4"), rates.rateFor(SupportedCurrency.KRW));
        assertEquals(BigDecimal.ONE, rates.rateFor(SupportedCurrency.USD));
        assertEquals(LocalDate.of(2026, 11, 20), rates.rateDate());
        assertTrue(rates.isLive());
    }

    @Test
    void rejectsAReplyWithAMissingCurrency() {
        String noKrw = VALID_REPLY.replace(",\"KRW\":1398.4", "");

        assertThrows(ExchangeRateException.class, () -> provider("https://example.invalid").parse(noKrw));
    }

    @Test
    void rejectsAReplyWithTheWrongBase() {
        String eurBase = VALID_REPLY.replace("\"base\":\"USD\"", "\"base\":\"EUR\"");

        assertThrows(ExchangeRateException.class, () -> provider("https://example.invalid").parse(eurBase));
    }

    @Test
    void rejectsZeroOrNegativeRates() {
        String zeroEur = VALID_REPLY.replace("\"EUR\":0.9512", "\"EUR\":0");

        assertThrows(ExchangeRateException.class, () -> provider("https://example.invalid").parse(zeroEur));
    }

    @Test
    void rejectsInvalidJson() {
        assertThrows(ExchangeRateException.class, () -> provider("https://example.invalid").parse("not json"));
    }

    @Test
    void missingDateIsAllowed() {
        String noDate = VALID_REPLY.replace("\"date\":\"2026-11-20\",", "");

        assertNull(provider("https://example.invalid").parse(noDate).rateDate());
    }

    // ---------- Real HTTP call against a small local test server ----------

    @Test
    void fetchesRatesOverHttp() throws IOException {
        AtomicReference<String> requestedQuery = new AtomicReference<>();
        String baseUrl = startServer(200, VALID_REPLY, requestedQuery);

        ExchangeRates rates = provider(baseUrl).fetchRates();

        assertEquals(new BigDecimal("151.23"), rates.rateFor(SupportedCurrency.JPY));
        assertEquals("base=USD&symbols=EUR,GBP,JPY,CAD,AUD,INR,KRW", requestedQuery.get());
    }

    @Test
    void throwsWhenTheApiReturnsAnError() throws IOException {
        String baseUrl = startServer(500, "{\"message\":\"oops\"}", new AtomicReference<>());

        assertThrows(ExchangeRateException.class, () -> provider(baseUrl).fetchRates());
    }

    @Test
    void throwsWhenTheApiCannotBeReached() throws IOException {
        // Start and stop a server so the port is (almost certainly) closed
        String baseUrl = startServer(200, VALID_REPLY, new AtomicReference<>());
        server.stop(0);
        server = null;

        assertThrows(ExchangeRateException.class, () -> provider(baseUrl).fetchRates());
    }

    // ---------- Helpers ----------

    private static FrankfurterExchangeRateProvider provider(String baseUrl) {
        return new FrankfurterExchangeRateProvider(new ObjectMapper(), baseUrl, 2);
    }

    // Starts a tiny HTTP server (built into the JDK) that answers /v1/latest with the given reply
    private String startServer(int status, String body, AtomicReference<String> requestedQuery) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/latest", exchange -> {
            requestedQuery.set(exchange.getRequestURI().getQuery());
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }
}
