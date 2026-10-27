package com.backend.eventsrus.service;

import com.backend.eventsrus.config.AppleProperties;
import com.backend.eventsrus.exception.InvalidAppleTokenException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.ProtectedHeader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.util.HexFormat;
import org.springframework.stereotype.Service;

/**
 * Verifies a Sign in with Apple identity token: RS256 signature against Apple's published keys, issuer
 * https://appleid.apple.com, audience = our bundle id, not expired, and the nonce claim equals the SHA-256 hex
 * digest of the raw nonce the app generated (stops a stolen token being replayed).
 */
@Service
public class AppleTokenVerifierService implements AppleIdentityTokenVerifier {

    static final String ISSUER = "https://appleid.apple.com";

    private final AppleKeySource keySource;
    private final AppleProperties properties;

    public AppleTokenVerifierService(AppleKeySource keySource, AppleProperties properties) {
        this.keySource = keySource;
        this.properties = properties;
    }

    @Override
    public AppleIdentity verify(String identityToken, String rawNonce) {
        if (identityToken == null || identityToken.isBlank() || rawNonce == null || rawNonce.isBlank()) {
            throw new InvalidAppleTokenException("Missing Apple identity token or nonce");
        }
        Claims claims;
        try {
            claims = Jwts.parser()
                    .keyLocator(header -> {
                        PublicKey key = header instanceof ProtectedHeader ph ? keySource.keyFor(ph.getKeyId()) : null;
                        if (key == null) {
                            throw new InvalidAppleTokenException("Unknown Apple signing key");
                        }
                        return key;
                    })
                    .requireIssuer(ISSUER)
                    .requireAudience(properties.getBundleId())
                    .build()
                    .parseSignedClaims(identityToken)
                    .getPayload();
        } catch (InvalidAppleTokenException ex) {
            throw ex;
        } catch (JwtException | IllegalArgumentException ex) {
            throw new InvalidAppleTokenException("Invalid Apple identity token", ex);
        }

        String nonceClaim = claims.get("nonce", String.class);
        if (nonceClaim == null || !MessageDigest.isEqual(
                nonceClaim.getBytes(StandardCharsets.UTF_8), sha256Hex(rawNonce).getBytes(StandardCharsets.UTF_8))) {
            throw new InvalidAppleTokenException("Apple identity token nonce does not match");
        }
        String subject = claims.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new InvalidAppleTokenException("Apple identity token has no subject");
        }
        return new AppleIdentity(
                subject,
                claims.get("email", String.class),
                truthy(claims.get("email_verified")),
                truthy(claims.get("is_private_email")));
    }

    /** Apple sends these flags as either a JSON boolean or the string "true". */
    private static boolean truthy(Object value) {
        return value instanceof Boolean b ? b : "true".equalsIgnoreCase(String.valueOf(value));
    }

    static String sha256Hex(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
