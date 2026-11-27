package com.tripbudgetplanner.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record ResendCodeRequest(
    @NotBlank(message = "Sign-in session is missing. Please sign in again.") String challengeId
) {}
