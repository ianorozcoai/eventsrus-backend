package com.backend.eventsrus.service;

import com.backend.eventsrus.dto.CalendarEntryResponse;
import com.backend.eventsrus.enums.BookingStatus;
import com.backend.eventsrus.enums.QuotationStatus;
import com.backend.eventsrus.model.Booking;
import com.backend.eventsrus.model.Conversation;
import com.backend.eventsrus.model.Event;
import com.backend.eventsrus.model.Quotation;
import com.backend.eventsrus.model.User;
import com.backend.eventsrus.model.VendorProfile;
import com.backend.eventsrus.repository.BookingRepository;
import com.backend.eventsrus.repository.ConversationRepository;
import com.backend.eventsrus.repository.EventRepository;
import com.backend.eventsrus.repository.QuotationRepository;
import com.backend.eventsrus.repository.UserRepository;
import com.backend.eventsrus.repository.VendorProfileRepository;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CalendarService {

    // APPROVED is the old cold-proposal flow's confirmed status; BOOKED is
    // both the old quotation-booking detour's and the newer
    // QuotationService#acceptBooking flow's confirmed status. A booking in
    // either counts as "actually booked" for calendar purposes - leaving
    // BOOKED out here was making every booking made through the current
    // Accept Booking flow invisible on both the vendor and planner calendars.
    private static final Set<BookingStatus> CONFIRMED_STATUSES = Set.of(BookingStatus.APPROVED, BookingStatus.BOOKED);

    // Every other status is still "in progress" from the vendor's
    // perspective - the calendar shows it (in violet, see the legend on
    // vendor/calendar.html) so a formal quotation request doesn't vanish
    // from view just because it hasn't been booked yet. BOOKED is excluded
    // here since that event already gets its own, more authoritative entry
    // from the Booking loop below; DECLINED is excluded since there's
    // nothing left to act on.
    private static final Set<QuotationStatus> TERMINAL_QUOTATION_STATUSES =
            Set.of(QuotationStatus.BOOKED, QuotationStatus.DECLINED);

    private static final ZoneId MANILA = ZoneId.of("Asia/Manila");

    private final BookingRepository bookingRepository;
    private final ConversationRepository conversationRepository;
    private final EventRepository eventRepository;
    private final QuotationRepository quotationRepository;
    private final UserRepository userRepository;
    private final VendorProfileRepository vendorProfileRepository;

    /**
     * Every saved event the planner is working on, not just ones with a
     * confirmed booking — a planner should see everything they're planning,
     * with vendor names filled in once a booking is actually approved.
     */
    @Transactional(readOnly = true)
    public List<CalendarEntryResponse> plannerCalendar(String plannerEmail) {
        User planner = requireUser(plannerEmail);

        Map<Long, List<String>> vendorNamesByEvent = new LinkedHashMap<>();
        for (Booking booking : bookingRepository.findByPlannerUserIdOrderByEventDatetimeAsc(planner.getId())) {
            if (!CONFIRMED_STATUSES.contains(booking.getStatus())) {
                continue;
            }
            VendorProfile vendorProfile = vendorProfileRepository.findByUserId(booking.getVendorUser().getId()).orElse(null);
            String vendorName = vendorProfile != null ? vendorProfile.getBusinessName() : displayName(booking.getVendorUser());
            vendorNamesByEvent.computeIfAbsent(booking.getEvent().getId(), id -> new ArrayList<>()).add(vendorName);
        }

        List<Event> savedEvents = eventRepository.findByPlannerIdAndSavedTrueOrderByCreatedAtDesc(planner.getId());
        List<CalendarEntryResponse> entries = new ArrayList<>();
        for (Event event : savedEvents) {
            List<String> vendorNames = vendorNamesByEvent.get(event.getId());
            entries.add(CalendarEntryResponse.builder()
                    .eventId(event.getId())
                    .eventName(event.getName())
                    .eventDatetime(event.getEventDate() != null
                            ? event.getEventDate().atStartOfDay(ZoneOffset.UTC).toInstant()
                            : null)
                    .status(vendorNames != null ? "BOOKED" : "PLANNED")
                    .vendorNames(vendorNames != null ? vendorNames : List.of())
                    .build());
        }
        return entries;
    }

    @Transactional(readOnly = true)
    public List<CalendarEntryResponse> vendorCalendar(String vendorEmail) {
        User vendor = requireUser(vendorEmail);

        Map<Long, CalendarEntryResponse> byEvent = new LinkedHashMap<>();

        for (Conversation conversation : conversationRepository.findByVendorUserIdOrderByUpdatedAtDesc(vendor.getId())) {
            byEvent.put(conversation.getEvent().getId(), CalendarEntryResponse.builder()
                    .eventId(conversation.getEvent().getId())
                    .eventName(conversation.getEvent().getName())
                    .eventDatetime(null)
                    .status("INQUIRY")
                    .plannerName(displayName(conversation.getPlannerUser()))
                    .build());
        }

        // A formal quotation request has no conversation behind it at all
        // when it comes straight from the storefront (not a chat) - without
        // this, it had no way onto the calendar until either a chat started
        // or it got all the way to BOOKED. Iterated after conversations and
        // before bookings so a real status escalation for the same event
        // (chat -> quotation -> booked) always overwrites with the more
        // advanced one, never the other way around.
        for (Quotation quotation : quotationRepository.findByVendorUserIdOrderByCreatedAtDesc(vendor.getId())) {
            if (TERMINAL_QUOTATION_STATUSES.contains(quotation.getStatus())) {
                continue;
            }
            byEvent.put(quotation.getEvent().getId(), CalendarEntryResponse.builder()
                    .eventId(quotation.getEvent().getId())
                    .eventName(quotation.getEvent().getName())
                    .eventDatetime(quotation.getTargetDate() != null
                            ? quotation.getTargetDate().atStartOfDay(MANILA).toInstant()
                            : null)
                    .status(quotation.getStatus().name())
                    .plannerName(displayName(quotation.getPlannerUser()))
                    .build());
        }

        for (Booking booking : bookingRepository.findByVendorUserIdOrderByEventDatetimeAsc(vendor.getId())) {
            if (CONFIRMED_STATUSES.contains(booking.getStatus())) {
                byEvent.put(booking.getEvent().getId(), CalendarEntryResponse.builder()
                        .eventId(booking.getEvent().getId())
                        .eventName(booking.getEvent().getName())
                        .eventDatetime(booking.getEventDatetime())
                        .status("BOOKED")
                        .plannerName(displayName(booking.getPlannerUser()))
                        .build());
            }
        }

        return List.copyOf(byEvent.values());
    }

    private User requireUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + email));
    }

    private String displayName(User user) {
        return user.getFirstName() != null ? user.getFirstName() : user.getEmail();
    }
}
