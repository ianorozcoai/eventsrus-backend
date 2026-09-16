package com.backend.eventsrus.service;

import com.backend.eventsrus.model.Event;
import com.backend.eventsrus.model.EventTabView;
import com.backend.eventsrus.model.User;
import com.backend.eventsrus.repository.BookingAmendmentRepository;
import com.backend.eventsrus.repository.BookingRepository;
import com.backend.eventsrus.repository.EventRepository;
import com.backend.eventsrus.repository.EventTabViewRepository;
import com.backend.eventsrus.repository.QuotationRepository;
import com.backend.eventsrus.repository.UserRepository;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Unseen since you last looked" counts for the vendor nav's Quotations/
 * Bookings badges and the planner's per-event Quotations/Bookings tab
 * badges - deliberately based on each viewer's own last-visit timestamp
 * against the record's updated_at, not the Notification table. A
 * notification-based count can't cover a vendor's own action (e.g.
 * confirming a booking themselves via QuotationService#acceptBooking) since
 * nobody notifies themselves of their own action - but that new booking is
 * still genuinely new information the vendor hasn't looked at on the
 * Bookings page yet, which is exactly what this tracks instead.
 *
 * Quotations only ever change through a real field mutation (every
 * QuotationService method saves the entity), so plain updated_at works.
 * Bookings mostly work the same way, except BookingAmendmentService#propose
 * never touches the Booking row itself (only inserts a new BookingAmendment)
 * - so a pending amendment awaiting this viewer's response is folded in
 * separately, and stays counted for as long as it's pending regardless of
 * age (unlike a plain status change, "seen it but haven't acted on it yet"
 * shouldn't make the badge disappear).
 */
@Service
@RequiredArgsConstructor
public class BadgeService {

    private static final Instant NEVER_SEEN = Instant.EPOCH;

    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final QuotationRepository quotationRepository;
    private final BookingRepository bookingRepository;
    private final BookingAmendmentRepository bookingAmendmentRepository;
    private final EventTabViewRepository eventTabViewRepository;

    @Transactional(readOnly = true)
    public long countUnseenQuotationsForVendor(Long vendorUserId) {
        Instant seenAt = requireUser(vendorUserId).getQuotationsBadgeSeenAt();
        return quotationRepository.countByVendorUserIdAndUpdatedAtAfter(vendorUserId, seenAt != null ? seenAt : NEVER_SEEN);
    }

    @Transactional(readOnly = true)
    public long countUnseenBookingsForVendor(Long vendorUserId) {
        Instant seenAt = requireUser(vendorUserId).getBookingsBadgeSeenAt();
        Instant threshold = seenAt != null ? seenAt : NEVER_SEEN;
        Set<Long> unseenBookingIds = new HashSet<>(bookingRepository.findIdsByVendorUserIdAndUpdatedAtAfter(vendorUserId, threshold));
        unseenBookingIds.addAll(bookingAmendmentRepository.findPendingBookingIdsAwaitingVendor(vendorUserId));
        return unseenBookingIds.size();
    }

    @Transactional
    public void markVendorQuotationsSeen(Long vendorUserId) {
        User vendor = requireUser(vendorUserId);
        vendor.setQuotationsBadgeSeenAt(Instant.now());
        userRepository.save(vendor);
    }

    @Transactional
    public void markVendorBookingsSeen(Long vendorUserId) {
        User vendor = requireUser(vendorUserId);
        vendor.setBookingsBadgeSeenAt(Instant.now());
        userRepository.save(vendor);
    }

    // Deliberately a distinct call the vendor's Quotations/Bookings page
    // handlers make themselves (see QuotationController/BookingController),
    // not a side effect tucked inside QuotationService#listForVendor /
    // BookingService#listForVendor - those two methods are also used by
    // pages that merely check for a quotation/booking's existence in
    // passing (e.g. VendorController#messages' "already has a quotation?"
    // check), which must never silently clear a badge the vendor hasn't
    // actually looked at.
    @Transactional
    public void markVendorQuotationsSeen(String vendorEmail) {
        markVendorQuotationsSeen(requireUserByEmail(vendorEmail).getId());
    }

    @Transactional
    public void markVendorBookingsSeen(String vendorEmail) {
        markVendorBookingsSeen(requireUserByEmail(vendorEmail).getId());
    }

    @Transactional(readOnly = true)
    public long countUnseenQuotationsForEvent(String plannerEmail, Long eventId) {
        return countUnseenQuotationsForEvent(requirePlannerOwnedEvent(plannerEmail, eventId), eventId);
    }

    @Transactional(readOnly = true)
    public long countUnseenBookingsForEvent(String plannerEmail, Long eventId) {
        return countUnseenBookingsForEvent(requirePlannerOwnedEvent(plannerEmail, eventId), eventId);
    }

    @Transactional
    public void markEventQuotationsSeen(String plannerEmail, Long eventId) {
        markEventQuotationsSeen(requirePlannerOwnedEvent(plannerEmail, eventId), eventId);
    }

    @Transactional
    public void markEventBookingsSeen(String plannerEmail, Long eventId) {
        markEventBookingsSeen(requirePlannerOwnedEvent(plannerEmail, eventId), eventId);
    }

    // The event's own planner id, once confirmed that plannerEmail actually
    // owns it - listForEvent-style endpoints have historically trusted
    // eventId alone, but these are new endpoints with no existing callers to
    // stay compatible with, so there's no reason not to check.
    private Long requirePlannerOwnedEvent(String plannerEmail, Long eventId) {
        User planner = requireUserByEmail(plannerEmail);
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalStateException("Event not found: " + eventId));
        if (!event.getPlanner().getId().equals(planner.getId())) {
            throw new IllegalStateException("Event does not belong to the authenticated planner");
        }
        return planner.getId();
    }

    @Transactional(readOnly = true)
    public long countUnseenQuotationsForEvent(Long plannerUserId, Long eventId) {
        Instant seenAt = findView(plannerUserId, eventId).map(EventTabView::getQuotationsSeenAt).orElse(NEVER_SEEN);
        return quotationRepository.countByEventIdAndUpdatedAtAfter(eventId, seenAt != null ? seenAt : NEVER_SEEN);
    }

    @Transactional(readOnly = true)
    public long countUnseenBookingsForEvent(Long plannerUserId, Long eventId) {
        Instant seenAt = findView(plannerUserId, eventId).map(EventTabView::getBookingsSeenAt).orElse(NEVER_SEEN);
        Instant threshold = seenAt != null ? seenAt : NEVER_SEEN;
        Set<Long> unseenBookingIds = new HashSet<>(bookingRepository.findIdsByEventIdAndUpdatedAtAfter(eventId, threshold));
        unseenBookingIds.addAll(bookingAmendmentRepository.findPendingBookingIdsAwaitingPlannerForEvent(plannerUserId, eventId));
        return unseenBookingIds.size();
    }

    @Transactional
    public void markEventQuotationsSeen(Long plannerUserId, Long eventId) {
        EventTabView view = requireView(plannerUserId, eventId);
        view.setQuotationsSeenAt(Instant.now());
        eventTabViewRepository.save(view);
    }

    @Transactional
    public void markEventBookingsSeen(Long plannerUserId, Long eventId) {
        EventTabView view = requireView(plannerUserId, eventId);
        view.setBookingsSeenAt(Instant.now());
        eventTabViewRepository.save(view);
    }

    private Optional<EventTabView> findView(Long userId, Long eventId) {
        return eventTabViewRepository.findByUserIdAndEventId(userId, eventId);
    }

    // Lazily creates the (user, event) row on first mark-seen - most
    // (user, event) pairs never get one at all (a planner who never opens
    // the Quotations/Bookings tab for a given event has nothing to track).
    private EventTabView requireView(Long userId, Long eventId) {
        return eventTabViewRepository.findByUserIdAndEventId(userId, eventId)
                .orElseGet(() -> EventTabView.builder()
                        .user(userRepository.getReferenceById(userId))
                        .event(Event.builder().id(eventId).build())
                        .build());
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User not found: " + userId));
    }

    private User requireUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + email));
    }
}
