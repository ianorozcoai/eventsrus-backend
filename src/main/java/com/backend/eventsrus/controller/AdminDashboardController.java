package com.backend.eventsrus.controller;

import com.backend.eventsrus.dto.AdminDashboardResponse;
import com.backend.eventsrus.enums.Role;
import com.backend.eventsrus.enums.SignupIntent;
import com.backend.eventsrus.enums.TicketStatus;
import com.backend.eventsrus.repository.SupportTicketRepository;
import com.backend.eventsrus.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Real counts for the admin Dashboard, protected by SecurityConfig's
 * existing /api/v1/admin/** -> hasRole("ADMIN") rule. "New" ticket counts
 * mean still OPEN - nobody's replied or actioned it yet.
 */
@RestController
@RequestMapping("/api/v1/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final UserRepository userRepository;
    private final SupportTicketRepository supportTicketRepository;

    @GetMapping
    public AdminDashboardResponse get() {
        return AdminDashboardResponse.builder()
                .plannerCount(userRepository.countByRoleAndSignupIntentNot(Role.PLANNER, SignupIntent.VENDOR))
                .vendorCount(userRepository.countByRole(Role.VENDOR))
                .vendorTicketCount(supportTicketRepository.countByRaisedBy_Role(Role.VENDOR))
                .newVendorTicketCount(supportTicketRepository.countByRaisedBy_RoleAndStatus(Role.VENDOR, TicketStatus.OPEN))
                .newPlannerTicketCount(supportTicketRepository.countByRaisedBy_RoleAndStatus(Role.PLANNER, TicketStatus.OPEN))
                .incompleteVendorSignupCount(userRepository.countBySignupIntentAndRoleNot(SignupIntent.VENDOR, Role.VENDOR))
                .build();
    }
}
