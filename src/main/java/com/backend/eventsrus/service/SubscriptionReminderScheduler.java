package com.backend.eventsrus.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Sends the daily renewal-reminder SMS run at 9:00 Philippine time (a sensible hour for a text). */
@Component
@RequiredArgsConstructor
public class SubscriptionReminderScheduler {

    private final SubscriptionReminderService subscriptionReminderService;

    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Manila")
    public void sendRenewalReminders() {
        subscriptionReminderService.runOnce();
    }
}
