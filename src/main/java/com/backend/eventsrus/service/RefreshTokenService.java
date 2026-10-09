package com.backend.eventsrus.service;

import com.backend.eventsrus.exception.InvalidRefreshTokenException;
import com.backend.eventsrus.model.RefreshToken;
import com.backend.eventsrus.model.User;
import com.backend.eventsrus.repository.RefreshTokenRepository;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues and redeems the long-lived refresh tokens that let a mobile
 * session stay signed in for weeks (see RefreshToken's own doc comment)
 * without the access token's 1-hour expiry (jwt.expiration-ms) forcing a
 * full re-login. Only ever stores/compares a SHA-256 hash of the raw
 * token - the raw value is returned to the caller exactly once, at issue
 * time, and never persisted or logged here.
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${jwt.refresh-expiration-ms}")
    private long refreshExpirationMs;

    /** Returns the raw token - this is the only time it's ever available in plaintext. */
    public String issue(User user) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(hash(rawToken))
                .expiresAt(Instant.now().plusMillis(refreshExpirationMs))
                .revoked(false)
                .build();
        refreshTokenRepository.save(refreshToken);

        return rawToken;
    }

    /**
     * Redeems a raw refresh token: rejects it if unknown, already revoked
     * (rotation already consumed it, or it was explicitly logged out), or
     * past its own expiry, otherwise marks it revoked (single-use - the
     * caller is expected to immediately {@link #issue} a replacement) and
     * returns the user it belongs to.
     */
    @Transactional
    public User validateAndRotate(String rawToken) {
        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new InvalidRefreshTokenException("Session expired. Please sign in again."));

        if (refreshToken.isRevoked() || refreshToken.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidRefreshTokenException("Session expired. Please sign in again.");
        }

        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);

        return refreshToken.getUser();
    }

    /** Revokes one specific refresh token - e.g. on explicit logout, so it can't be redeemed again. */
    @Transactional
    public void revoke(String rawToken) {
        refreshTokenRepository.findByTokenHash(hash(rawToken))
                .ifPresent(refreshToken -> {
                    refreshToken.setRevoked(true);
                    refreshTokenRepository.save(refreshToken);
                });
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes()));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
