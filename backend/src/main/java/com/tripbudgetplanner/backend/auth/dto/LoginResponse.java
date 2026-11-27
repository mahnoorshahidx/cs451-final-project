package com.tripbudgetplanner.backend.auth.dto;

// What POST /api/auth/login returns.
// With two-factor on: twoFactorRequired = true, a challengeId and where the code went; no token yet.
// With it off: twoFactorRequired = false and the token, as before.
// devCode is only filled when app.auth.two-factor.show-code=true (demo mode).
public record LoginResponse(
    boolean twoFactorRequired,
    String challengeId,
    String sentTo,
    Long expiresInSeconds,
    Long resendAvailableInSeconds,
    String devCode,
    String token,
    String name,
    String email
) {
    public static LoginResponse signedIn(AuthResponse auth) {
        return new LoginResponse(false, null, null, null, null, null, auth.token(), auth.name(), auth.email());
    }

    public static LoginResponse challenge(String challengeId, String sentTo, long expiresInSeconds,
                                          long resendAvailableInSeconds, String devCode) {
        return new LoginResponse(true, challengeId, sentTo, expiresInSeconds, resendAvailableInSeconds, devCode,
            null, null, null);
    }
}
