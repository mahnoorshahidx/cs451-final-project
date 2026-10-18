package com.tripbudgetplanner.backend.auth.dto;

public record AuthResponse(String token, String name, String email) {}