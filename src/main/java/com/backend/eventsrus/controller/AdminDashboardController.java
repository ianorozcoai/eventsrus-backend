package com.backend.eventsrus.controller;

import com.backend.eventsrus.dto.AdminDashboardResponse;
import com.backend.eventsrus.dto.BusinessTypeSupplierCountResponse;
import com.backend.eventsrus.enums.BusinessType;
import com.backend.eventsrus.enums.Role;
import com.backend.eventsrus.enums.SignupIntent;
import com.backend.eventsrus.enums.TicketStatus;
import com.backend.eventsrus.repository.SupportTicketRepository;
import com.backend.eventsrus.repository.UserRepository;
import com.backend.eventsrus.repository.VendorProfileRepository;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
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
    private final VendorProfileRepository vendorProfileRepository;

    @GetMapping
    public AdminDashboardResponse get() {
        return AdminDashboardResponse.builder()
                .plannerCount(userRepository.countByRoleAndSignupIntentNot(Role.PLANNER, SignupIntent.VENDOR))
                .vendorCount(userRepository.countByRoleAndFakeAccountFalse(Role.VENDOR))
                .vendorTicketCount(supportTicketRepository.countByRaisedBy_Role(Role.VENDOR))
                .newVendorTicketCount(supportTicketRepository.countByRaisedBy_RoleAndStatus(Role.VENDOR, TicketStatus.OPEN))
                .newPlannerTicketCount(supportTicketRepository.countByRaisedBy_RoleAndStatus(Role.PLANNER, TicketStatus.OPEN))
                .incompleteVendorSignupCount(userRepository.countBySignupIntentAndRoleNot(SignupIntent.VENDOR, Role.VENDOR))
                .supplierCountsByBusinessType(supplierCountsByBusinessType())
                .build();
    }

    // Real (non-fake) vendor count per business type - the repository query
    // only returns rows for types with at least one match, so every other
    // BusinessType gets filled in at zero here rather than just missing
    // from the response (the admin should see "0" for a type nobody's in
    // yet, not have it silently absent from the list).
    private List<BusinessTypeSupplierCountResponse> supplierCountsByBusinessType() {
        Map<BusinessType, Long> counts = new EnumMap<>(BusinessType.class);
        for (Object[] row : vendorProfileRepository.countRealVendorsByBusinessType()) {
            counts.put((BusinessType) row[0], (Long) row[1]);
        }
        return Arrays.stream(BusinessType.values())
                .map(bt -> BusinessTypeSupplierCountResponse.builder()
                        .businessType(bt)
                        .count(counts.getOrDefault(bt, 0L))
                        .build())
                .sorted(Comparator.comparing(BusinessTypeSupplierCountResponse::getCount).reversed())
                .toList();
    }
}
