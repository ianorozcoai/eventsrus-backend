package com.backend.eventsrus.service;

import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Downloads https://appleid.apple.com/auth/keys and caches it for an hour (re-reading at most once a minute when
 * a token names a key id we do not know yet).
 */
@Slf4j
@Service
public class AppleJwksKeySource implements AppleKeySource {

    private static final URI KEYS_URI = URI.create("https://appleid.apple.com/auth/keys");
    private static final Duration CACHE_FOR = Duration.ofHours(1);
    private static final Duration MIN_REFETCH = Duration.ofMinutes(1);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final JsonMapper json = JsonMapper.builder().build();
    private Map<String, PublicKey> keys = Map.of();
    private Instant fetchedAt = Instant.MIN;

    @Override
    public synchronized PublicKey keyFor(String keyId) {
        if (keyId == null) {
            return null;
        }
        Instant now = Instant.now();
        boolean stale = fetchedAt.equals(Instant.MIN) || fetchedAt.plus(CACHE_FOR).isBefore(now);
        boolean unknownAndAllowedToRefetch = !keys.containsKey(keyId) && fetchedAt.plus(MIN_REFETCH).isBefore(now);
        if (stale || unknownAndAllowedToRefetch) {
            refresh(now);
        }
        return keys.get(keyId);
    }

    private void refresh(Instant now) {
        try {
            HttpResponse<String> response = http.send(
                    HttpRequest.newBuilder(KEYS_URI).timeout(Duration.ofSeconds(10)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Apple keys request failed with HTTP {}", response.statusCode());
                return;
            }
            Map<String, PublicKey> fresh = new HashMap<>();
            for (JsonNode jwk : json.readTree(response.body()).path("keys")) {
                if (!"RSA".equals(jwk.path("kty").asString(null))) {
                    continue;
                }
                BigInteger n = new BigInteger(1, Base64.getUrlDecoder().decode(jwk.path("n").asString("")));
                BigInteger e = new BigInteger(1, Base64.getUrlDecoder().decode(jwk.path("e").asString("")));
                fresh.put(jwk.path("kid").asString(""),
                        KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(n, e)));
            }
            if (!fresh.isEmpty()) {
                keys = fresh;
                fetchedAt = now;
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            log.warn("Could not load Apple signing keys: {}", ex.toString());
        }
    }
}
