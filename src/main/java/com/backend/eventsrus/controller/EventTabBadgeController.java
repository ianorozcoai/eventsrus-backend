package com.backend.eventsrus.controller;

import com.backend.eventsrus.service.BadgeService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * "Unseen since I last opened this tab" counts for a planner's per-event
 * Quotations/Bookings tabs - see BadgeService. Kept separate from
 * QuotationController/BookingController since both tabs already render
 * server-side in one page load (planner/events.html's Bootstrap tabs, not
 * separate page navigations) - marking a tab "seen" has to be a distinct
 * client-triggered call fired when the planner actually clicks it, not a
 * side effect of the page's initial data fetch the way it can be for the
 * vendor's own separate /vendor/quotations and /vendor/bookings pages (see
 * QuotationService#listForVendor / BookingService#listForVendor).
 */
@RestController
@RequiredArgsConstructor
public class EventTabBadgeController {

    private final BadgeService badgeService;

    @GetMapping("/api/v1/events/{eventId}/quotations/unseen-count")
    public Map<String, Long> unseenQuotations(@PathVariable Long eventId, Authentication authentication) {
        return Map.of("count", badgeService.countUnseenQuotationsForEvent(authentication.getName(), eventId));
    }

    @GetMapping("/api/v1/events/{eventId}/bookings/unseen-count")
    public Map<String, Long> unseenBookings(@PathVariable Long eventId, Authentication authentication) {
        return Map.of("count", badgeService.countUnseenBookingsForEvent(authentication.getName(), eventId));
    }

    @PutMapping("/api/v1/events/{eventId}/quotations/mark-seen")
    public void markQuotationsSeen(@PathVariable Long eventId, Authentication authentication) {
        badgeService.markEventQuotationsSeen(authentication.getName(), eventId);
    }

    @PutMapping("/api/v1/events/{eventId}/bookings/mark-seen")
    public void markBookingsSeen(@PathVariable Long eventId, Authentication authentication) {
        badgeService.markEventBookingsSeen(authentication.getName(), eventId);
    }
}
