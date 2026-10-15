package com.backend.eventsrus.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Daily sweep that finishes deletions whose 30-day grace period is over. */
@Component
@RequiredArgsConstructor
@Slf4j
public class AccountDeletionScheduler {

    private final AccountDeletionService accountDeletionService;
    private final InactiveSupplierCleanupService inactiveSupplierCleanupService;

    // Lapsed-supplier cleanup; a no-op unless app.inactivity-cleanup-enabled=true (production only).
    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Manila")
    public void cleanUpLapsedSuppliers() {
        int actions = inactiveSupplierCleanupService.runOnce();
        if (actions > 0) {
            log.info("Lapsed-supplier cleanup finished: {} action(s)", actions);
        }
    }

    // 03:30 Philippine time, when traffic is lowest.
    @Scheduled(cron = "0 30 3 * * *", zone = "Asia/Manila")
    public void purgeDueAccounts() {
        int purged = accountDeletionService.purgeDueAccounts();
        if (purged > 0) {
            log.info("Account deletion sweep finished: {} account(s) scrubbed", purged);
        }
    }
}
