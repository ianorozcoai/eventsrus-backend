package com.backend.eventsrus.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "revenuecat")
@Getter
@Setter
public class RevenueCatProperties {

    /** Value RevenueCat sends in the Authorization header. Blank means the webhook is disabled. */
    private String webhookSecret;
}
