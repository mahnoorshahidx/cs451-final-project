package com.tripbudgetplanner.backend.auth.twofactor;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

// Demo delivery: the code appears in the backend console instead of an email.
// Fine for a class demo; swap in a real sender before using this with real accounts.
@Component
public class LogVerificationCodeSender implements VerificationCodeSender {

    private static final Logger log = LoggerFactory.getLogger(LogVerificationCodeSender.class);

    @Override
    public void send(String email, String code, Duration validFor) {
        log.warn("Sign-in code for {}: {} (valid for {} minutes)", email, code, validFor.toMinutes());
    }
}
