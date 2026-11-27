package com.tripbudgetplanner.backend.auth.twofactor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import com.tripbudgetplanner.backend.auth.dto.LoginResponse;
import com.tripbudgetplanner.backend.user.User;

class TwoFactorServiceTest {

    // A clock the test can move forward
    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-11-27T12:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
        void advance(Duration d) { now = now.plus(d); }
    }

    private final Map<String, LoginChallenge> store = new HashMap<>();
    private final List<String> sentCodes = new ArrayList<>();
    private MutableClock clock;
    private TwoFactorService service;
    private User user;

    @BeforeEach
    void setUp() {
        LoginChallengeRepository repo = mock(LoginChallengeRepository.class);
        when(repo.save(any(LoginChallenge.class))).thenAnswer(inv -> {
            LoginChallenge c = inv.getArgument(0);
            store.put(c.getId(), c);
            return c;
        });
        when(repo.findById(anyString())).thenAnswer(inv -> Optional.ofNullable(store.get((String) inv.getArgument(0))));
        when(repo.findByUserIdAndClosedAtIsNull(anyLong())).thenAnswer(inv -> store.values().stream()
            .filter(c -> c.getUserId().equals(inv.getArgument(0)) && c.getClosedAt() == null)
            .toList());

        clock = new MutableClock();
        VerificationCodeSender sender = (email, code, validFor) -> sentCodes.add(code);
        // BCrypt strength 4 keeps the tests fast
        service = new TwoFactorService(repo, new BCryptPasswordEncoder(4), sender,
            true, 10, 3, 30, 3, false, clock);

        user = mock(User.class);
        when(user.getId()).thenReturn(7L);
        when(user.getEmail()).thenReturn("hafsa@example.com");
    }

    private String lastCode() {
        return sentCodes.get(sentCodes.size() - 1);
    }

    private HttpStatus statusOf(Runnable call) {
        ResponseStatusException e = assertThrows(ResponseStatusException.class, call::run);
        return HttpStatus.valueOf(e.getStatusCode().value());
    }

    @Test
    void startSendsASixDigitCodeAndStoresOnlyItsHash() {
        LoginResponse res = service.start(user);

        assertTrue(res.twoFactorRequired());
        assertNull(res.token());
        assertNull(res.devCode(), "the code is only shown in demo mode");
        assertEquals("h****@example.com", res.sentTo());
        assertEquals(600L, res.expiresInSeconds());
        assertTrue(lastCode().matches("\\d{6}"));

        LoginChallenge saved = store.get(res.challengeId());
        assertNotEquals(lastCode(), saved.getCodeHash());
    }

    @Test
    void rightCodeReturnsTheUserOnce() {
        LoginResponse res = service.start(user);

        assertEquals(7L, service.verify(res.challengeId(), lastCode()));
        assertEquals(HttpStatus.GONE, statusOf(() -> service.verify(res.challengeId(), lastCode())));
    }

    @Test
    void wrongCodesCountDownThenCloseTheSignIn() {
        LoginResponse res = service.start(user);
        String wrong = lastCode().equals("000000") ? "111111" : "000000";

        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> service.verify(res.challengeId(), wrong)));
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> service.verify(res.challengeId(), wrong)));
        assertEquals(HttpStatus.GONE, statusOf(() -> service.verify(res.challengeId(), wrong)));
        // Even the right code no longer works
        assertEquals(HttpStatus.GONE, statusOf(() -> service.verify(res.challengeId(), lastCode())));
    }

    @Test
    void expiredCodeIsRejected() {
        LoginResponse res = service.start(user);
        clock.advance(Duration.ofMinutes(10));

        assertEquals(HttpStatus.GONE, statusOf(() -> service.verify(res.challengeId(), lastCode())));
    }

    @Test
    void unknownChallengeIsRejected() {
        assertEquals(HttpStatus.GONE, statusOf(() -> service.verify("not-a-real-id", "123456")));
    }

    @Test
    void resendWaitsForTheCooldownAndReplacesTheCode() {
        LoginResponse res = service.start(user);
        String first = lastCode();

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, statusOf(() -> service.resend(res.challengeId())));

        clock.advance(Duration.ofSeconds(30));
        service.resend(res.challengeId());
        String second = lastCode();

        if (!first.equals(second)) {
            assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> service.verify(res.challengeId(), first)));
        }
        assertEquals(7L, service.verify(res.challengeId(), second));
    }

    @Test
    void resendStopsAtTheCap() {
        LoginResponse res = service.start(user);
        clock.advance(Duration.ofSeconds(30));
        service.resend(res.challengeId());
        clock.advance(Duration.ofSeconds(30));
        service.resend(res.challengeId());
        clock.advance(Duration.ofSeconds(30));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, statusOf(() -> service.resend(res.challengeId())));
    }

    @Test
    void newSignInClosesTheOlderOne() {
        LoginResponse first = service.start(user);
        String firstCode = lastCode();
        service.start(user);

        assertEquals(HttpStatus.GONE, statusOf(() -> service.verify(first.challengeId(), firstCode)));
    }

    @Test
    void demoModeShowsTheCode() {
        TwoFactorService demo = new TwoFactorService(mock(LoginChallengeRepository.class), new BCryptPasswordEncoder(4),
            (email, code, validFor) -> sentCodes.add(code), true, 10, 3, 30, 3, true, clock);
        LoginResponse res = demo.start(user);

        assertEquals(lastCode(), res.devCode());
    }

    @Test
    void masksEmails() {
        assertEquals("a*@b.com", TwoFactorService.maskEmail("ab@b.com"));
        assertEquals("a*@b.com", TwoFactorService.maskEmail("a@b.com"));
        assertEquals("your email", TwoFactorService.maskEmail(null));
    }
}
