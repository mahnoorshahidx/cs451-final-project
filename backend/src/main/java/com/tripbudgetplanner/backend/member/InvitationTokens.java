package com.tripbudgetplanner.backend.member;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

// Creates invitation tokens and the hashes we store for them.
// A token is 32 random bytes (256 bits) from SecureRandom, so it can't be guessed.
// Only the SHA-256 hash is saved, so a leaked database can't be turned into working links.
public final class InvitationTokens {

    private static final SecureRandom RANDOM = new SecureRandom();

    private InvitationTokens() {}

    // A new URL-safe token, 43 characters long
    public static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // SHA-256 of the token as 64 lowercase hex characters
    public static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // Every Java runtime includes SHA-256
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
