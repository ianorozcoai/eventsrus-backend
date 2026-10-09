package com.backend.eventsrus.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import java.io.FileInputStream;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Initializes the Firebase Admin SDK (PushNotificationSenderService's own
 * client) from a service account key downloaded from the Firebase console
 * (Project Settings > Service accounts > Generate new private key) -
 * firebase.service-account-path points at that file, which is never
 * committed (see .gitignore). Missing/invalid file just logs a warning and
 * leaves push-sending off, the same graceful-degradation this mirrors on
 * the mobile side (PushNotificationService's own doc comment) - nothing
 * else in the app depends on this.
 */
@Slf4j
@Configuration
public class FirebaseConfig {

    @Value("${firebase.service-account-path:firebase-service-account.json}")
    private String serviceAccountPath;

    @PostConstruct
    public void initialize() {
        try (FileInputStream serviceAccount = new FileInputStream(serviceAccountPath)) {
            if (!FirebaseApp.getApps().isEmpty()) {
                return;
            }
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();
            FirebaseApp.initializeApp(options);
            log.info("Firebase Admin SDK initialized - push notifications can be sent.");
        } catch (IOException e) {
            log.warn("Firebase service account not found/invalid at '{}' - push notifications are disabled "
                    + "until this is set up. ({})", serviceAccountPath, e.getMessage());
        }
    }
}
