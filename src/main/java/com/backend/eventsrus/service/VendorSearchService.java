package com.backend.eventsrus.service;

import com.backend.eventsrus.enums.BusinessType;
import com.backend.eventsrus.model.VendorProfile;
import com.backend.eventsrus.repository.VendorProfileRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class VendorSearchService {

    private static final ZoneId MANILA = ZoneId.of("Asia/Manila");

    private final VendorProfileRepository vendorProfileRepository;
    private final VendorPlanService vendorPlanService;

    /**
     * Real vendors whose operating areas cover {@code province} (or who
     * serve "Entire Philippines"), match {@code businessType}, and have an
     * active subscription. {@code eventDate} is optional - when given, also
     * excludes vendors whose declared lead time (VendorProfile#leadTimeDays)
     * makes that date too soon for them to take on. Deliberately NOT
     * filtering by maxCustomersPerDay/existing booking counts here - a
     * vendor who's already busy can simply decline an inquiry themselves;
     * the app isn't in the business of guaranteeing availability.
     */
    public List<VendorProfile> findMatchingVendors(BusinessType businessType, String province, LocalDate eventDate) {
        if (province == null || province.isBlank()) {
            return List.of();
        }
        return vendorProfileRepository.findByBusinessTypeAndOperatingArea(businessType, province.trim()).stream()
                .filter(this::hasActiveSubscription)
                .filter(vp -> meetsLeadTime(vp, eventDate))
                .toList();
    }

    private boolean meetsLeadTime(VendorProfile profile, LocalDate eventDate) {
        if (eventDate == null || profile.getLeadTimeDays() == null) {
            return true;
        }
        LocalDate earliestBookable = LocalDate.now(MANILA).plusDays(profile.getLeadTimeDays());
        return !eventDate.isBefore(earliestBookable);
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
