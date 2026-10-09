package com.backend.eventsrus.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "semaphore")
@Getter
@Setter
public class SemaphoreProperties {

    private String baseUrl;
    private String apiKey;
    private String senderName;
}
