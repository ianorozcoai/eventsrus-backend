package com.backend.eventsrus.controller;

import com.backend.eventsrus.dto.SupportTicketResponse;
import com.backend.eventsrus.enums.Role;
import com.backend.eventsrus.service.SupportTicketService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin's read view over every support ticket, split by who raised it
 * (VENDOR complaints vs PLANNER complaints), protected by SecurityConfig's
 * existing /api/v1/admin/** -> hasRole("ADMIN") rule. Viewing a thread,
 * replying, and changing status all already exist on the real, shared
 * SupportTicketController endpoints - any authenticated admin can already
 * act on any ticket there, not just their own (see
 * SupportTicketService#reply / #getMessages / #updateStatus). This only
 * adds the one thing that was actually missing: a way to LIST every ticket
 * instead of just the caller's own.
 */
@RestController
@RequestMapping("/api/v1/admin/support-tickets")
@RequiredArgsConstructor
public class AdminSupportTicketController {

    private final SupportTicketService supportTicketService;

    @GetMapping
    public List<SupportTicketResponse> list(@RequestParam(required = false) Role raisedByRole) {
        return supportTicketService.listForAdmin(raisedByRole);
    }
}
