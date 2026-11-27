package com.tripbudgetplanner.backend.auth.twofactor;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.tripbudgetplanner.backend.auth.dto.LoginResponse;
import com.tripbudgetplanner.backend.user.User;

// UC1 two-factor sign-in: after the password is right, a 6-digit code is sent and must be entered
// before a token is issued.
// - Codes are random, stored hashed, and valid for a few minutes (app.auth.two-factor.code-minutes).
// - Too many wrong codes close the challenge; the user signs in again.
// - Resending has a cooldown and a cap, and replaces the previous code.
@Service
public class TwoFactorService {

    private final LoginChallengeRepository challenges;
    private final PasswordEncoder encoder;
    private final VerificationCodeSender sender;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    private final boolean enabled;
    private final Duration codeValidFor;
    private final int maxAttempts;
    private final Duration resendCooldown;
    private final int maxSends;
    private final boolean showCode;

    @Autowired
    public TwoFactorService(LoginChallengeRepository challenges, PasswordEncoder encoder, VerificationCodeSender sender,
                            @Value("${app.auth.two-factor.enabled:true}") boolean enabled,
                            @Value("${app.auth.two-factor.code-minutes:10}") long codeMinutes,
                            @Value("${app.auth.two-factor.max-attempts:5}") int maxAttempts,
                            @Value("${app.auth.two-factor.resend-seconds:30}") long resendSeconds,
                            @Value("${app.auth.two-factor.max-sends:5}") int maxSends,
                            @Value("${app.auth.two-factor.show-code:false}") boolean showCode) {
        this(challenges, encoder, sender, enabled, codeMinutes, maxAttempts, resendSeconds, maxSends, showCode,
            Clock.systemUTC());
    }

    // For tests: a fixed or adjustable clock
    TwoFactorService(LoginChallengeRepository challenges, PasswordEncoder encoder, VerificationCodeSender sender,
                     boolean enabled, long codeMinutes, int maxAttempts, long resendSeconds, int maxSends,
                     boolean showCode, Clock clock) {
        this.challenges = challenges;
        this.encoder = encoder;
        this.sender = sender;
        this.enabled = enabled;
        this.codeValidFor = Duration.ofMinutes(Math.max(1, codeMinutes));
        this.maxAttempts = Math.max(1, maxAttempts);
        this.resendCooldown = Duration.ofSeconds(Math.max(0, resendSeconds));
        this.maxSends = Math.max(1, maxSends);
        this.showCode = showCode;
        this.clock = clock;
    }

    public boolean isEnabled() {
        return enabled;
    }

    // Password was right: close any older open sign-in, then send a fresh code
    @Transactional
    public LoginResponse start(User user) {
        Instant now = clock.instant();
        for (LoginChallenge open : challenges.findByUserIdAndClosedAtIsNull(user.getId())) {
            open.setClosedAt(now);
            challenges.save(open);
        }

        LoginChallenge challenge = new LoginChallenge();
        challenge.setId(UUID.randomUUID().toString());
        challenge.setUserId(user.getId());
        challenge.setEmail(user.getEmail());
        challenge.setCreatedAt(now);
        challenge.setSendCount(0);
        String code = issueCode(challenge, now);
        challenges.save(challenge);

        sender.send(challenge.getEmail(), code, codeValidFor);
        return challengeResponse(challenge, code, now);
    }

    // Checks the code. Returns the user id when it's right; the challenge can't be used again.
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public Long verify(String challengeId, String code) {
        Instant now = clock.instant();
        LoginChallenge challenge = openChallenge(challengeId, now);

        if (code == null || !encoder.matches(code.trim(), challenge.getCodeHash())) {
            challenge.setAttempts(challenge.getAttempts() + 1);
            int left = maxAttempts - challenge.getAttempts();
            if (left <= 0) {
                challenge.setClosedAt(now);
                challenges.save(challenge);
                throw new ResponseStatusException(HttpStatus.GONE, "Too many wrong codes. Please sign in again.");
            }
            challenges.save(challenge);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "That code isn't right. " + left + (left == 1 ? " try" : " tries") + " left.");
        }

        challenge.setClosedAt(now);
        challenges.save(challenge);
        return challenge.getUserId();
    }

    // Sends a new code (the old one stops working), after the cooldown and up to the cap
    @Transactional
    public LoginResponse resend(String challengeId) {
        Instant now = clock.instant();
        LoginChallenge challenge = openChallenge(challengeId, now);

        long wait = secondsUntilResend(challenge, now);
        if (wait > 0) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                "Please wait " + wait + " seconds before asking for a new code.");
        }
        if (challenge.getSendCount() >= maxSends) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                "Too many codes sent. Please sign in again.");
        }

        String code = issueCode(challenge, now);
        challenges.save(challenge);
        sender.send(challenge.getEmail(), code, codeValidFor);
        return challengeResponse(challenge, code, now);
    }

    // ---------------- helpers ----------------

    private LoginChallenge openChallenge(String challengeId, Instant now) {
        LoginChallenge challenge = challengeId == null ? null : challenges.findById(challengeId).orElse(null);
        if (challenge == null || challenge.getClosedAt() != null || !now.isBefore(challenge.getExpiresAt())) {
            throw new ResponseStatusException(HttpStatus.GONE, "This code has expired. Please sign in again.");
        }
        return challenge;
    }

    // New random code on the challenge: resets the timer and the wrong-code count
    private String issueCode(LoginChallenge challenge, Instant now) {
        String code = String.format("%06d", random.nextInt(1_000_000));
        challenge.setCodeHash(encoder.encode(code));
        challenge.setExpiresAt(now.plus(codeValidFor));
        challenge.setLastSentAt(now);
        challenge.setAttempts(0);
        challenge.setSendCount(challenge.getSendCount() + 1);
        return code;
    }

    private long secondsUntilResend(LoginChallenge challenge, Instant now) {
        long elapsed = Duration.between(challenge.getLastSentAt(), now).toSeconds();
        return Math.max(0, resendCooldown.toSeconds() - elapsed);
    }

    private LoginResponse challengeResponse(LoginChallenge challenge, String code, Instant now) {
        return LoginResponse.challenge(
            challenge.getId(),
            maskEmail(challenge.getEmail()),
            Duration.between(now, challenge.getExpiresAt()).toSeconds(),
            secondsUntilResend(challenge, now),
            showCode ? code : null);
    }

    // "hafsa@example.com" -> "h****@example.com"
    static String maskEmail(String email) {
        int at = email == null ? -1 : email.indexOf('@');
        if (at <= 0) return "your email";
        String name = email.substring(0, at);
        return name.charAt(0) + "*".repeat(Math.max(1, name.length() - 1)) + email.substring(at);
    }
}
