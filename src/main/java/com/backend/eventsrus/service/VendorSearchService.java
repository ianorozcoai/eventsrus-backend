package com.backend.eventsrus.service;

import com.backend.eventsrus.enums.BusinessType;
import com.backend.eventsrus.enums.EventType;
import com.backend.eventsrus.model.VendorProfile;
import com.backend.eventsrus.repository.VendorProfileRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
     * makes that date too soon for them to take on. {@code eventType} is
     * optional - when given, excludes vendors who have declared the event
     * types they cater to (VendorProfile#cateredEventTypes) and didn't
     * include this one; a vendor who hasn't declared any is left in.
     * Deliberately NOT filtering by maxCustomersPerDay/existing booking
     * counts here - a vendor who's already busy can simply decline an
     * inquiry themselves; the app isn't in the business of guaranteeing
     * availability.
     */
    public List<VendorProfile> findMatchingVendors(
            BusinessType businessType, String province, LocalDate eventDate, EventType eventType) {
        if (province == null || province.isBlank()) {
            return List.of();
        }
        return vendorProfileRepository.findByBusinessTypeAndOperatingArea(businessType, province.trim()).stream()
                .filter(this::hasActiveSubscription)
                .filter(vp -> meetsLeadTime(vp, eventDate))
                .filter(vp -> catersToEventType(vp, eventType))
                // Top Vendors first, then verified, so the spotlight/trusted
                // ones get the top of their category.
                .sorted(Comparator.comparing(VendorProfile::isTopVendor)
                        .thenComparing(VendorProfile::isVerified).reversed())
                .toList();
    }

    private boolean meetsLeadTime(VendorProfile profile, LocalDate eventDate) {
        if (eventDate == null || profile.getLeadTimeDays() == null) {
            return true;
        }
        LocalDate earliestBookable = LocalDate.now(MANILA).plusDays(profile.getLeadTimeDays());
        return !eventDate.isBefore(earliestBookable);
    }

    // A vendor who has spelled out which event types they cater to only
    // matches when this event's type is one of them. A vendor who hasn't
    // declared any (e.g. onboarded before it was collected) isn't filtered
    // out - absence of data isn't a "no".
    private boolean catersToEventType(VendorProfile profile, EventType eventType) {
        if (eventType == null || profile.getCateredEventTypes() == null || profile.getCateredEventTypes().isEmpty()) {
            return true;
        }
        return profile.getCateredEventTypes().contains(eventType);
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

    /**
     * Deliberate search for a specific vendor the planner already has in
     * mind, by (partial, case-insensitive) business name - distinct from
     * findMatchingVendors above, which only ever surfaces vendors the AI
     * picked for one event's category/location/date. {@code query} is
     * required (a blank query returns nothing rather than every vendor);
     * {@code businessType} further narrows an already-blank query's name
     * matches, it doesn't stand alone as a browse-everything mode.
     * {@code @Transactional} so businessTypes (a LAZY collection) can be
     * force-initialized below before returning - the caller (controller
     * layer, building SuggestedVendorResponse.businessTypes for the
     * ungrouped result card) runs after this method's session has closed.
     */
    @Transactional(readOnly = true)
    public List<VendorProfile> searchVendors(String query, BusinessType businessType) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        List<VendorProfile> results = vendorProfileRepository.findByBusinessNameContainingIgnoreCase(query.trim()).stream()
                .filter(this::hasActiveSubscription)
                .filter(vp -> businessType == null || vp.getBusinessTypes().contains(businessType))
                .sorted(Comparator.comparing(VendorProfile::isTopVendor)
                        .thenComparing(VendorProfile::isVerified)
                        .reversed()
                        .thenComparing(VendorProfile::getBusinessName, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        results.forEach(vp -> vp.getBusinessTypes().size());
        return results;
    }

    // A vendor whose subscription has lapsed stops being recommended to
    // planners - they still exist and can be reached directly (an existing
    // conversation/booking isn't affected), just not surfaced as a match.
    private boolean hasActiveSubscription(VendorProfile profile) {
        // A deleted supplier never shows up, even if a cancelled plan still has days left on it.
        if (profile.getUser().getDeletedAt() != null) {
            return false;
        }
        return vendorPlanService.getEffectivePlan(profile.getUser().getId()).plan() != null;
    }
}
