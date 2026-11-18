package com.tripbudgetplanner.backend.ai;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

// Thin wrapper around the Anthropic Messages API (https://docs.claude.com).
// Uses Java's built-in HttpClient, so no extra Maven dependency is needed.
//
// The API key comes from the ANTHROPIC_API_KEY environment variable (see application.properties).
// With no key set, isEnabled() is false and callers use the rule-based provider instead,
// so the app still runs for teammates who don't have a key.
@Component
public class AnthropicClient {

    private static final Logger log = LoggerFactory.getLogger(AnthropicClient.class);
    private static final String API_VERSION = "2023-06-01";

    private final ObjectMapper mapper;
    private final HttpClient http;
    private final String apiKey;
    private final String model;
    private final URI endpoint;
    private final Duration timeout;
    private final int maxTokens;

    public AnthropicClient(
        ObjectMapper mapper,
        @Value("${app.ai.api-key:}") String apiKey,
        @Value("${app.ai.model:claude-haiku-5-5}") String model,
        @Value("${app.ai.base-url:https://api.anthropic.com}") String baseUrl,
        @Value("${app.ai.timeout-seconds:20}") int timeoutSeconds,
        @Value("${app.ai.max-tokens:1024}") int maxTokens
    ) {
        this.mapper = mapper;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
        this.endpoint = URI.create(baseUrl.replaceAll("/+$", "") + "/v1/messages");
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.maxTokens = maxTokens;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

        if (this.apiKey.isEmpty()) {
            log.info("ANTHROPIC_API_KEY is not set: AI recommendations are off, using rule-based recommendations.");
        } else {
            log.info("AI recommendations are on (model {}).", model);
        }
    }

    public boolean isEnabled() {
        return !apiKey.isEmpty();
    }

    // Sends one system + user message and returns the model's text reply.
    // Throws AiClientException on any network, HTTP or format problem.
    public String complete(String systemPrompt, String userPrompt) {
        return complete(systemPrompt, userPrompt, maxTokens, timeout);
    }

    // Same as above, with a custom length limit and timeout for longer replies (e.g. itineraries).
    public String complete(String systemPrompt, String userPrompt, int maxTokens, Duration timeout) {
        if (!isEnabled()) {
            throw new AiClientException("AI is not configured (no API key).");
        }

        ObjectNode body = mapper.createObjectNode();
        body.put("model", model);
        body.put("max_tokens", maxTokens);
        body.put("system", systemPrompt);
        ArrayNode messages = body.putArray("messages");
        ObjectNode user = messages.addObject();
        user.put("role", "user");
        user.put("content", userPrompt);

        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(endpoint)
                .timeout(timeout)
                .header("content-type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", API_VERSION)
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();
        } catch (JacksonException e) {
            throw new AiClientException("Could not build the AI request.", e);
        }

        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new AiClientException("AI request failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiClientException("AI request was interrupted.", e);
        }

        if (response.statusCode() != 200) {
            // Don't log the body in full: it can be long. The status code is enough to debug most problems
            // (401 = bad key, 429 = rate limited, 529 = overloaded).
            throw new AiClientException("AI request returned HTTP " + response.statusCode() + ".");
        }

        try {
            JsonNode root = mapper.readTree(response.body());
            StringBuilder text = new StringBuilder();
            JsonNode content = root.path("content");
            for (int i = 0; i < content.size(); i++) {
                JsonNode block = content.get(i);
                if ("text".equals(block.path("type").asString())) {
                    text.append(block.path("text").asString());
                }
            }
            if (text.isEmpty()) {
                throw new AiClientException("AI response had no text.");
            }
            return text.toString();
        } catch (JacksonException e) {
            throw new AiClientException("Could not read the AI response.", e);
        }
    }

    // Parses the model's reply as JSON, tolerating ```json fences or text around the object.
    public JsonNode parseJsonObject(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new AiClientException("AI reply was not JSON.");
        }
        try {
            JsonNode node = mapper.readTree(text.substring(start, end + 1));
            if (!node.isObject()) {
                throw new AiClientException("AI reply was not a JSON object.");
            }
            return node;
        } catch (JacksonException e) {
            throw new AiClientException("AI reply was not valid JSON.", e);
        }
    }
}