package com.backend.eventsrus.controller;

import com.backend.eventsrus.dto.AdminPlannerListItemResponse;
import com.backend.eventsrus.service.UserService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Real planner directory, protected by SecurityConfig's existing /api/v1/admin/** -> hasRole("ADMIN") rule. */
@RestController
@RequestMapping("/api/v1/admin/planners")
@RequiredArgsConstructor
public class AdminPlannerController {

    private final UserService userService;

    @GetMapping
    public List<AdminPlannerListItemResponse> list() {
        return userService.listPlannersForAdmin().stream()
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/{userId}")
    public AdminPlannerListItemResponse get(@PathVariable Long userId) {
        return toResponse(userService.getPlannerForAdmin(userId));
    }

    private AdminPlannerListItemResponse toResponse(UserService.AdminPlannerListItem item) {
        return AdminPlannerListItemResponse.builder()
                .id(item.id())
                .firstName(item.firstName())
                .lastName(item.lastName())
                .email(item.email())
                .mobileNumber(item.mobileNumber())
                .city(item.city())
                .state(item.state())
                .joinedAt(item.joinedAt())
                .eventsCount(item.eventsCount())
                .build();
    }
}
