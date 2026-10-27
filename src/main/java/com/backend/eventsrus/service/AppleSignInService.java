package com.backend.eventsrus.service;

import com.backend.eventsrus.config.AppleProperties;
import io.jsonwebtoken.Jwts;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/**
 * The server-to-server half of Sign in with Apple: after a sign-in it can swap the one-time authorization code
 * for a refresh token (kept encrypted), and when an account is deleted it revokes that token at Apple, which
 * Apple expects of apps that offer both Sign in with Apple and account deletion. Everything here is a no-op
 * until the Apple key settings (see {@link AppleProperties}) are set, so the app works without them.
 */
@Slf4j
@Service
public class AppleSignInService {

    private static final String TOKEN_URL = "https://appleid.apple.com/auth/token";
    private static final String REVOKE_URL = "https://appleid.apple.com/auth/revoke";
    private static final String AUDIENCE = "https://appleid.apple.com";

    private final AppleProperties properties;
    private final AppleHttpClient http;
    private final TokenEncryptionService encryption;
    private final JsonMapper json = JsonMapper.builder().build();

    public AppleSignInService(AppleProperties properties, AppleHttpClient http, TokenEncryptionService encryption) {
        this.properties = properties;
        this.http = http;
        this.encryption = encryption;
    }

    public boolean isConfigured() {
        return properties.revokeConfigured();
    }

    /** @return the Apple refresh token, encrypted for storage; null when not configured or Apple refused */
    public String exchangeCodeForEncryptedRefreshToken(String authorizationCode) {
        if (!isConfigured() || authorizationCode == null || authorizationCode.isBlank()) {
            return null;
        }
        try {
            AppleHttpClient.Result result = http.postForm(TOKEN_URL, Map.of(
                    "client_id", properties.getBundleId(),
                    "client_secret", clientSecret(),
                    "code", authorizationCode,
                    "grant_type", "authorization_code"));
            if (result.status() != 200) {
                log.warn("Apple token exchange failed with HTTP {}", result.status());
                return null;
            }
            String refresh = json.readTree(result.body()).path("refresh_token").asString(null);
            return refresh == null ? null : encryption.encrypt(refresh);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            log.warn("Apple token exchange could not be completed: {}", e.toString());
            return null;
        }
    }

    /** Best effort: never throws, so a failure at Apple cannot block an account deletion. */
    public void revokeQuietly(String encryptedRefreshToken) {
        if (!isConfigured() || encryptedRefreshToken == null || encryptedRefreshToken.isBlank()) {
            return;
        }
        try {
            AppleHttpClient.Result result = http.postForm(REVOKE_URL, Map.of(
                    "client_id", properties.getBundleId(),
                    "client_secret", clientSecret(),
                    "token", encryption.decrypt(encryptedRefreshToken),
                    "token_type_hint", "refresh_token"));
            if (result.status() != 200) {
                log.warn("Apple token revoke failed with HTTP {}", result.status());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("Apple token revoke could not be completed: {}", e.toString());
        }
    }

    /** The "client secret" Apple wants is a short-lived ES256 JWT signed with the Sign-in-with-Apple key (.p8). */
    String clientSecret() throws Exception {
        Instant now = Instant.now();
        return Jwts.builder()
                .header().keyId(properties.getKeyId()).and()
                .issuer(properties.getTeamId())
                .subject(properties.getBundleId())
                .audience().add(AUDIENCE).and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(300)))
                .signWith(privateKey(), Jwts.SIG.ES256)
                .compact();
    }

    private PrivateKey privateKey() throws Exception {
        String pem = properties.getPrivateKey()
                .replace("\\n", "\n")
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        return KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)));
    }
}
