package com.backend.eventsrus.controller;

import com.backend.eventsrus.dto.AdminBlockResponse;
import com.backend.eventsrus.dto.AdminReportResponse;
import com.backend.eventsrus.dto.BlockedUserResponse;
import com.backend.eventsrus.dto.ReportRequest;
import com.backend.eventsrus.dto.ReportResponse;
import com.backend.eventsrus.dto.ResolveReportRequest;
import com.backend.eventsrus.enums.ReportStatus;
import com.backend.eventsrus.service.ModerationService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Report and block for signed-in users, plus the admin's read/resolve view (the admin routes sit
 * under /api/v1/admin/**, already limited to ADMIN by SecurityConfig). See ModerationService.
 */
@RestController
@RequiredArgsConstructor
public class ModerationController {

    private final ModerationService moderationService;

    @PostMapping("/api/v1/reports")
    @ResponseStatus(HttpStatus.CREATED)
    public ReportResponse report(@Valid @RequestBody ReportRequest request, Authentication authentication) {
        return moderationService.report(authentication.getName(), request);
    }

    @PostMapping("/api/v1/users/{userId}/block")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void block(@PathVariable Long userId, Authentication authentication) {
        moderationService.block(authentication.getName(), userId);
    }

    @DeleteMapping("/api/v1/users/{userId}/block")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unblock(@PathVariable Long userId, Authentication authentication) {
        moderationService.unblock(authentication.getName(), userId);
    }

    @GetMapping("/api/v1/users/me/blocks")
    public List<BlockedUserResponse> blocked(Authentication authentication) {
        return moderationService.listBlocked(authentication.getName());
    }

    /** Who has blocked whom, newest first - see ModerationService#listBlocksForAdmin. */
    @GetMapping("/api/v1/admin/blocks")
    public List<AdminBlockResponse> adminBlocks() {
        return moderationService.listBlocksForAdmin();
    }

    @GetMapping("/api/v1/admin/reports")
    public List<AdminReportResponse> adminList(@RequestParam(required = false) ReportStatus status) {
        return moderationService.listForAdmin(status);
    }

    @PostMapping("/api/v1/admin/reports/{reportId}/resolve")
    public AdminReportResponse adminResolve(
            @PathVariable Long reportId, @Valid @RequestBody(required = false) ResolveReportRequest request) {
        return moderationService.resolve(reportId, request == null ? null : request.getNote());
    }
}
