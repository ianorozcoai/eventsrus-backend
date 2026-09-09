package com.backend.eventsrus.service;

import com.backend.eventsrus.config.AnthropicProperties;
import com.backend.eventsrus.exception.AnthropicApiException;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Thin wrapper around Claude's Messages API (api.anthropic.com/v1/messages).
 * One call in, one answer text out - no tool use, no multi-turn loop on our
 * side (the caller re-sends whatever context it needs each time). Kept
 * generic on purpose so any future AI-backed feature can reuse it rather
 * than each growing its own HTTP client, the same way PayPalSubscriptionClient
 * is the one place that knows how to talk to PayPal.
 */
@Service
@RequiredArgsConstructor
public class AnthropicClient {

    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final AnthropicProperties anthropicProperties;
    private final RestClient restClient = RestClient.create();

    public String ask(String systemPrompt, String userMessage) {
        if (anthropicProperties.getApiKey() == null || anthropicProperties.getApiKey().isBlank()) {
            throw new AnthropicApiException(
                    "The events coordinator isn't configured yet. Set ANTHROPIC_API_KEY to enable it.");
        }

        Map<String, Object> body = Map.of(
                "model", anthropicProperties.getModel(),
                "max_tokens", anthropicProperties.getMaxOutputTokens(),
                "system", systemPrompt,
                "messages", List.of(Map.of("role", "user", "content", userMessage)));

        try {
            MessageResponse response = restClient.post()
                    .uri(anthropicProperties.getBaseUrl() + "/v1/messages")
                    .header("x-api-key", anthropicProperties.getApiKey())
                    .header("anthropic-version", ANTHROPIC_VERSION)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(MessageResponse.class);

            if (response == null || response.content() == null || response.content().isEmpty()) {
                throw new AnthropicApiException("Empty response from the events coordinator");
            }

            String text = response.content().get(0).text();
            if (text == null || text.isBlank()) {
                throw new AnthropicApiException("Empty response from the events coordinator");
            }
            return text;
        } catch (AnthropicApiException e) {
            throw e;
        } catch (Exception e) {
            throw new AnthropicApiException("Failed to reach the events coordinator", e);
        }
    }

    // Only the fields we actually read - Claude's real response also
    // carries id/role/model/usage/etc, silently ignored (no
    // FAIL_ON_UNKNOWN_PROPERTIES configured anywhere in this app).
    private record MessageResponse(List<ContentBlock> content) {
        private record ContentBlock(String type, String text) {
        }
    }
}
