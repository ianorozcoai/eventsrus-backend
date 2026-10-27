package com.backend.eventsrus.controller;

import com.backend.eventsrus.dto.AdminAccountDeletionResponse;
import com.backend.eventsrus.service.AccountDeletionService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin visibility of account-deletion requests (a Google Play requirement we must be able to show we honour).
 * Read-only; the routes sit under /api/v1/admin/**, already limited to ADMIN by SecurityConfig.
 */
@RestController
@RequestMapping("/api/v1/admin/account-deletions")
@RequiredArgsConstructor
public class AdminAccountDeletionController {

    private final AccountDeletionService accountDeletionService;

    @GetMapping
    public List<AdminAccountDeletionResponse> list() {
        return accountDeletionService.listForAdmin();
    }
}
