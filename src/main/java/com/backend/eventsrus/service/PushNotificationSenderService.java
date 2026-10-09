package com.backend.eventsrus.service;

import com.backend.eventsrus.model.DeviceToken;
import com.backend.eventsrus.repository.DeviceTokenRepository;
import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Actually sends a push notification (the registration side is
 * DeviceTokenService). Called from NotificationService.notify(), so every
 * real event that already raises an in-app notification (new message,
 * lead, quotation/booking status change, ...) also triggers a push.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PushNotificationSenderService {

    private final DeviceTokenRepository deviceTokenRepository;

    /**
     * Returns how many of this user's devices a push was actually sent to.
     * relatedEntityType/relatedEntityId (same values stored on the in-app
     * Notification row - "CONVERSATION", "QUOTATION", "BOOKING", ...) ride
     * along as the FCM data payload so the mobile app can deep-link to the
     * right screen when the notification is tapped.
     */
    public int sendToUser(Long userId, String title, String body, String relatedEntityType, Long relatedEntityId) {
        if (FirebaseApp.getApps().isEmpty()) {
            log.warn("Firebase Admin SDK isn't initialized (no service account configured) - cannot send push.");
            return 0;
        }

        List<DeviceToken> deviceTokens = deviceTokenRepository.findByUserId(userId);
        int sent = 0;
        for (DeviceToken deviceToken : deviceTokens) {
            Message.Builder builder = Message.builder()
                    .setToken(deviceToken.getToken())
                    .setNotification(Notification.builder().setTitle(title).setBody(body).build());
            if (relatedEntityType != null) {
                builder.putData("type", relatedEntityType);
                if (relatedEntityId != null) {
                    builder.putData("id", relatedEntityId.toString());
                }
            }
            try {
                FirebaseMessaging.getInstance().send(builder.build());
                sent++;
            } catch (FirebaseMessagingException e) {
                log.warn("Could not send push to device token id {}: {}", deviceToken.getId(), e.getMessage());
            }
        }
        return sent;
    }
}
