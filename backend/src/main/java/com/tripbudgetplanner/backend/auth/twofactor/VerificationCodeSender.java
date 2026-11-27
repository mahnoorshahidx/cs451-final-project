package com.tripbudgetplanner.backend.auth.twofactor;

import java.time.Duration;

// Delivers a sign-in code. The default implementation writes it to the backend log;
// an email or SMS sender can replace it by implementing this interface as a @Primary bean.
@FunctionalInterface
public interface VerificationCodeSender {

    void send(String email, String code, Duration validFor);
}
