package com.tripbudgetplanner.backend.member;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class InvitationTokensTest {

    @Test
    void tokensAreLongUrlSafeAndUnique() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            String token = InvitationTokens.newToken();
            assertEquals(43, token.length(), "32 random bytes in URL-safe base64");
            assertTrue(token.matches("[A-Za-z0-9_-]+"), "safe to put in a URL");
            assertTrue(seen.add(token), "no repeats");
        }
    }

    @Test
    void storedHashIsNotTheTokenAndIsStable() {
        String token = InvitationTokens.newToken();
        String hash = InvitationTokens.hash(token);

        assertEquals(64, hash.length(), "fits the CHAR(64) column");
        assertTrue(hash.matches("[0-9a-f]+"));
        assertNotEquals(token, hash);
        assertEquals(hash, InvitationTokens.hash(token), "same token, same hash");
        assertNotEquals(hash, InvitationTokens.hash(InvitationTokens.newToken()));
    }

    @Test
    void hashMatchesKnownSha256() {
        // SHA-256("abc"), a standard test vector
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            InvitationTokens.hash("abc"));
    }
}
