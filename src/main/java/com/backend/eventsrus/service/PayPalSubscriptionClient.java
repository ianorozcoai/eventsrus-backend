package com.backend.eventsrus.service;

import com.backend.eventsrus.config.PayPalProperties;
import com.backend.eventsrus.exception.PayPalApiException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@RequiredArgsConstructor
public class PayPalSubscriptionClient {

    private final PayPalProperties payPalProperties;
    private final PayPalTokenService payPalTokenService;
    private final ObjectMapper objectMapper;
    private final RestClient restClient = RestClient.create();

    @SuppressWarnings("unchecked")
    public CreatedSubscription createSubscription(
            String planId, String customId, String returnUrl, String cancelUrl) {
        Map<String, Object> body = Map.of(
                "plan_id", planId,
                "custom_id", customId,
                "application_context", Map.of(
                        "return_url", returnUrl,
                        "cancel_url", cancelUrl,
                        "user_action", "SUBSCRIBE_NOW"));

        try {
            Map<String, Object> response = restClient.post()
                    .uri(payPalProperties.getBaseUrl() + "/v1/billing/subscriptions")
                    .headers(h -> h.addAll(payPalTokenService.authorizedJsonHeaders()))
                    .body(body)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });

            if (response == null) {
                throw new PayPalApiException("Empty response creating PayPal subscription");
            }

            String id = (String) response.get("id");
            List<Map<String, Object>> links = (List<Map<String, Object>>) response.get("links");
            String approveUrl = links == null ? null : links.stream()
                    .filter(link -> "approve".equals(link.get("rel")))
                    .map(link -> (String) link.get("href"))
                    .findFirst()
                    .orElse(null);

            if (id == null || approveUrl == null) {
                throw new PayPalApiException("PayPal response missing id/approve link");
            }

            return new CreatedSubscription(id, approveUrl);
        } catch (PayPalApiException e) {
            throw e;
        } catch (Exception e) {
            throw new PayPalApiException("Failed to create PayPal subscription", e);
        }
    }

    @SuppressWarnings("unchecked")
    public PayPalSubscriptionDetails getSubscription(String paypalSubscriptionId) {
        try {
            Map<String, Object> response = restClient.get()
                    .uri(payPalProperties.getBaseUrl() + "/v1/billing/subscriptions/{id}", paypalSubscriptionId)
                    .headers(h -> h.addAll(payPalTokenService.authorizedJsonHeaders()))
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });

            if (response == null) {
                throw new PayPalApiException("Empty response fetching PayPal subscription");
            }

            String status = (String) response.get("status");
            Map<String, Object> billingInfo = (Map<String, Object>) response.get("billing_info");
            Instant nextBillingTime = null;
            if (billingInfo != null && billingInfo.get("next_billing_time") != null) {
                nextBillingTime = Instant.parse((String) billingInfo.get("next_billing_time"));
            }

            return new PayPalSubscriptionDetails(status, nextBillingTime);
        } catch (PayPalApiException e) {
            throw e;
        } catch (Exception e) {
            throw new PayPalApiException("Failed to fetch PayPal subscription", e);
        }
    }

    /**
     * Verifies a webhook's authenticity via PayPal's verify-webhook-signature API.
     * {@code rawEventBody} is the exact JSON body PayPal sent, embedded verbatim
     * (parsed, not stringified) as required by that endpoint.
     */
    public boolean verifyWebhookSignature(
            String transmissionId,
            String transmissionTime,
            String certUrl,
            String authAlgo,
            String transmissionSig,
            String rawEventBody) {
        try {
            JsonNode eventNode = objectMapper.readTree(rawEventBody);

            Map<String, Object> body = Map.of(
                    "transmission_id", transmissionId,
                    "transmission_time", transmissionTime,
                    "cert_url", certUrl,
                    "auth_algo", authAlgo,
                    "transmission_sig", transmissionSig,
                    "webhook_id", payPalProperties.getWebhookId(),
                    "webhook_event", eventNode);

            Map<String, Object> response = restClient.post()
                    .uri(payPalProperties.getBaseUrl() + "/v1/notifications/verify-webhook-signature")
                    .headers(h -> h.addAll(payPalTokenService.authorizedJsonHeaders()))
                    .body(body)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });

            return response != null && "SUCCESS".equals(response.get("verification_status"));
        } catch (Exception e) {
            throw new PayPalApiException("Failed to verify PayPal webhook signature", e);
        }
    }

    public record CreatedSubscription(String paypalSubscriptionId, String approveUrl) {
    }

    public record PayPalSubscriptionDetails(String status, Instant nextBillingTime) {
    }
}
