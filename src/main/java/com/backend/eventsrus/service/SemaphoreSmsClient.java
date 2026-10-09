package com.backend.eventsrus.service;

import com.backend.eventsrus.config.SemaphoreProperties;
import com.backend.eventsrus.exception.SmsApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Thin wrapper around Semaphore's (semaphore.co) SMS REST API - same "plain
 * RestClient, no SDK" idiom as GoogleCalendarClient/PayPalSubscriptionClient.
 * Sender name "EventsRUsPH" is pending Semaphore's own approval as of
 * 2026-10-05; until it's approved, real sends from this client will likely
 * fail or fall back to Semaphore's default sender - see SmsService's
 * app.sms-enabled gate, which keeps this client from being called at all
 * until that's flipped on deliberately.
 */
@Service
@RequiredArgsConstructor
public class SemaphoreSmsClient {

    private final SemaphoreProperties semaphoreProperties;
    private final RestClient restClient = RestClient.create();

    public void send(String number, String message) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("apikey", semaphoreProperties.getApiKey());
        body.add("number", number);
        body.add("message", message);
        body.add("sendername", semaphoreProperties.getSenderName());

        try {
            restClient.post()
                    .uri(semaphoreProperties.getBaseUrl() + "/messages")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            throw new SmsApiException("Failed to send SMS via Semaphore to " + number, e);
        }
    }
}
