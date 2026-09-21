package com.backend.eventsrus.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "paypal")
@Getter
@Setter
public class PayPalProperties {

    private String baseUrl;
    private String clientId;
    private String clientSecret;
    private String webhookId;
    private PlanId planId = new PlanId();

    @Getter
    @Setter
    public static class PlanId {
        private String proMonthly;
        private String proQuarterly;
        private String proSemiAnnual;
        private String proAnnual;
        private String premiumMonthly;
        private String premiumAnnual;
    }
}
