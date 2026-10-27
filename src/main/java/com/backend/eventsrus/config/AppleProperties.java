package com.backend.eventsrus.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Sign in with Apple settings. Only {@code bundleId} is needed to verify sign-ins (the identity token's audience).
 * {@code teamId}, {@code keyId} and {@code privateKey} (the .p8 contents, set as an environment variable on the
 * host, never in a file) are only needed to revoke the Apple authorization when an account is deleted; while they
 * are blank that step is skipped.
 */
@Configuration
@ConfigurationProperties(prefix = "apple.signin")
@Getter
@Setter
public class AppleProperties {

    private String bundleId = "com.eventsrus.eventsrusui";
    private String teamId = "";
    private String keyId = "";
    private String privateKey = "";

    public boolean revokeConfigured() {
        return notBlank(teamId) && notBlank(keyId) && notBlank(privateKey);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
