package com.tripbudgetplanner.backend.common.security;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationMs;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expiration-ms}") long expirationMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    // The token's subject is the user's id, so controllers can identify the caller
    public String generateToken(Long userId, String email) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("email", email)
                .issuedAt(new Date(now))
                .expiration(new Date(now + expirationMs))
                .signWith(key)
                .compact();
    }

    // Returns the user id from a valid token, or null if invalid/expired
    public Long extractUserId(String token) {
        try {
            String subject = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload().getSubject();
            return Long.valueOf(subject);
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }
}