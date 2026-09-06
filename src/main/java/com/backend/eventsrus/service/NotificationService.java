package com.backend.eventsrus.service;

import com.backend.eventsrus.dto.NotificationResponse;
import com.backend.eventsrus.enums.NotificationType;
import com.backend.eventsrus.model.Notification;
import com.backend.eventsrus.model.User;
import com.backend.eventsrus.repository.NotificationRepository;
import com.backend.eventsrus.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    @Transactional
    public void notify(User recipient, NotificationType type, String title, String body,
            String relatedEntityType, Long relatedEntityId) {
        notificationRepository.save(Notification.builder()
                .recipient(recipient)
                .type(type)
                .title(title)
                .body(body)
                .relatedEntityType(relatedEntityType)
                .relatedEntityId(relatedEntityId)
                .read(false)
                .build());
    }

    public List<NotificationResponse> listNotifications(String email) {
        User user = requireUser(email);
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    public long countUnread(String email) {
        User user = requireUser(email);
        return notificationRepository.countByRecipientIdAndReadFalse(user.getId());
    }

    @Transactional
    public NotificationResponse markRead(String email, Long notificationId) {
        User user = requireUser(email);
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new IllegalStateException("Notification not found: " + notificationId));
        if (!notification.getRecipient().getId().equals(user.getId())) {
            throw new IllegalStateException("Notification does not belong to the authenticated user");
        }
        notification.setRead(true);
        notificationRepository.save(notification);
        return toResponse(notification);
    }

    private User requireUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + email));
    }

    private NotificationResponse toResponse(Notification notification) {
        return NotificationResponse.builder()
                .id(notification.getId())
                .type(notification.getType())
                .title(notification.getTitle())
                .body(notification.getBody())
                .relatedEntityType(notification.getRelatedEntityType())
                .relatedEntityId(notification.getRelatedEntityId())
                .read(notification.isRead())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
