package com.backend.eventsrus.service;

import com.backend.eventsrus.model.DeviceToken;
import com.backend.eventsrus.model.User;
import com.backend.eventsrus.repository.DeviceTokenRepository;
import com.backend.eventsrus.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registration side of push notifications (see DeviceToken's own doc
 * comment) - actually sending a push happens in
 * NotificationService/PushNotificationSenderService, triggered by the same
 * events that raise an in-app notification.
 */
@Service
@RequiredArgsConstructor
public class DeviceTokenService {

    private final DeviceTokenRepository deviceTokenRepository;
    private final UserRepository userRepository;

    @Transactional
    public void register(String userEmail, String token, String platform) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + userEmail));

        DeviceToken deviceToken = deviceTokenRepository.findByToken(token)
                .orElseGet(() -> DeviceToken.builder().token(token).build());
        deviceToken.setUser(user);
        deviceToken.setPlatform(platform);
        deviceTokenRepository.save(deviceToken);
    }

    @Transactional
    public void unregister(String userEmail, String token) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + userEmail));
        deviceTokenRepository.deleteByUserIdAndToken(user.getId(), token);
    }
}
