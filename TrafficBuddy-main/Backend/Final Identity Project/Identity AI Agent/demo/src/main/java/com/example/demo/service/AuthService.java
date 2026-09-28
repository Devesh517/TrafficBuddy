package com.example.demo.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightweight signin gate for the exhibition/demo build - no database, just a single
 * hardcoded demo account and in-memory session tokens.
 *
 * DEMO LOGIN: username = demo, password = demo123
 *
 * Not intended for production use - swap DEMO_USERNAME/DEMO_PASSWORD checks for a real
 * user store (and tokens for signed JWTs / Spring Security) once this goes past the
 * exhibition stage.
 */
@Service
public class AuthService {

    private static final String DEMO_USERNAME = "demo";
    private static final String DEMO_PASSWORD = "demo123";
    private static final long TOKEN_TTL_MINUTES = 60;

    // token -> expiry
    private final Map<String, Instant> activeTokens = new ConcurrentHashMap<>();

    /**
     * Checks the given credentials against the hardcoded demo account.
     * Returns a session token on success, or null on failure.
     */
    public String login(String username, String password) {
        if (username == null || password == null) return null;
        if (DEMO_USERNAME.equals(username.trim()) && DEMO_PASSWORD.equals(password)) {
            String token = UUID.randomUUID().toString();
            activeTokens.put(token, Instant.now().plus(TOKEN_TTL_MINUTES, ChronoUnit.MINUTES));
            return token;
        }
        return null;
    }

    /** Checks whether a token is present and not expired. */
    public boolean isValidToken(String token) {
        if (token == null || token.isBlank()) return false;
        Instant expiry = activeTokens.get(token);
        if (expiry == null) return false;
        if (Instant.now().isAfter(expiry)) {
            activeTokens.remove(token);
            return false;
        }
        return true;
    }
}