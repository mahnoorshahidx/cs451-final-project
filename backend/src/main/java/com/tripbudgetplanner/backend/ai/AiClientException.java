package com.tripbudgetplanner.backend.ai;

// Thrown when the AI call fails or returns something we can't use.
// Callers catch it and fall back to non-AI behavior.
public class AiClientException extends RuntimeException {

    public AiClientException(String message) {
        super(message);
    }

    public AiClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
