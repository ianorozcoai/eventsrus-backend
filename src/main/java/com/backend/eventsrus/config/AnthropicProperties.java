package com.backend.eventsrus.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Claude API config for the planner-facing "Events Coordinator" ideas/advice
 * assistant (see CoordinatorService). apiKey defaults to empty (see
 * application.properties) - AnthropicClient fails closed with a clear
 * "not configured yet" error rather than crashing on startup or sending a
 * doomed request, same pattern as app.internal-admin-key.
 */
@Configuration
@ConfigurationProperties(prefix = "anthropic")
@Getter
@Setter
public class AnthropicProperties {

    private String apiKey;
    private String baseUrl;
    private String model;
    private int maxOutputTokens;
}
