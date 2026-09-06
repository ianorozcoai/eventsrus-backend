package com.backend.eventsrus.service;

import com.backend.eventsrus.config.PayPalProperties;
import com.backend.eventsrus.exception.PayPalApiException;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@Service
@RequiredArgsConstructor
public class PayPalTokenService {

    private final PayPalProperties payPalProperties;
    private final RestClient restClient = RestClient.create();

    private String cachedToken;
    private Instant cachedTokenExpiresAt = Instant.EPOCH;

    public synchronized String getAccessToken() {
        if (cachedToken != null && Instant.now().isBefore(cachedTokenExpiresAt)) {
            return cachedToken;
        }

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "client_credentials");

        try {
            Map<String, Object> response = restClient.post()
                    .uri(payPalProperties.getBaseUrl() + "/v1/oauth2/token")
                    .headers(headers -> headers.setBasicAuth(
                            payPalProperties.getClientId(), payPalProperties.getClientSecret()))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body)
                    .retrieve()
                    .body(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {
                    });

            if (response == null || response.get("access_token") == null) {
                throw new PayPalApiException("PayPal token response missing access_token");
            }

            cachedToken = (String) response.get("access_token");
            int expiresInSeconds = ((Number) response.getOrDefault("expires_in", 0)).intValue();
            // Refresh a little early to avoid racing against expiry mid-request.
            cachedTokenExpiresAt = Instant.now().plusSeconds(Math.max(expiresInSeconds - 60, 0));

            return cachedToken;
        } catch (PayPalApiException e) {
            throw e;
        } catch (Exception e) {
            throw new PayPalApiException("Failed to obtain PayPal access token", e);
        }
    }

    HttpHeaders authorizedJsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(getAccessToken());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
