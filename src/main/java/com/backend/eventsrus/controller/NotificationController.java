package com.backend.eventsrus.controller;

import com.backend.eventsrus.dto.NotificationResponse;
import com.backend.eventsrus.service.NotificationService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public List<NotificationResponse> listNotifications(Authentication authentication) {
        return notificationService.listNotifications(authentication.getName());
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(Authentication authentication) {
        return Map.of("count", notificationService.countUnread(authentication.getName()));
    }

    @PutMapping("/{notificationId}/read")
    public NotificationResponse markRead(@PathVariable Long notificationId, Authentication authentication) {
        return notificationService.markRead(authentication.getName(), notificationId);
    }
}
