package com.backend.eventsrus.service;

import com.backend.eventsrus.enums.BusinessType;
import com.backend.eventsrus.model.VendorProfile;
import com.backend.eventsrus.repository.VendorProfileRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class VendorSearchService {

    private final VendorProfileRepository vendorProfileRepository;
    private final VendorPlanService vendorPlanService;

    /** Real vendors whose operating areas cover {@code province} (or who serve "Entire Philippines") and match {@code businessType}. */
    public List<VendorProfile> findMatchingVendors(BusinessType businessType, String province) {
        if (province == null || province.isBlank()) {
            return List.of();
        }
        return vendorProfileRepository.findByBusinessTypeAndOperatingArea(businessType, province.trim()).stream()
                .filter(this::hasActiveSubscription)
                .toList();
    }

    /** All vendors operating in {@code city}, regardless of category — for a general directory browse. */
    public List<VendorProfile> findVendorsInCity(String city) {
        if (city == null || city.isBlank()) {
            return List.of();
        }
        return vendorProfileRepository.findByCityIgnoreCase(city.trim()).stream()
                .filter(this::hasActiveSubscription)
                .toList();
    }

    // A vendor whose subscription has lapsed stops being recommended to
    // planners - they still exist and can be reached directly (an existing
    // conversation/booking isn't affected), just not surfaced as a match.
    private boolean hasActiveSubscription(VendorProfile profile) {
        return vendorPlanService.getEffectivePlan(profile.getUser().getId()).plan() != null;
    }
}
