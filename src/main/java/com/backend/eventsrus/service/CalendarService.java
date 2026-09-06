package com.backend.eventsrus.service;

import com.backend.eventsrus.dto.CalendarEntryResponse;
import com.backend.eventsrus.enums.BookingStatus;
import com.backend.eventsrus.model.Booking;
import com.backend.eventsrus.model.Conversation;
import com.backend.eventsrus.model.Event;
import com.backend.eventsrus.model.User;
import com.backend.eventsrus.model.VendorProfile;
import com.backend.eventsrus.repository.BookingRepository;
import com.backend.eventsrus.repository.ConversationRepository;
import com.backend.eventsrus.repository.EventRepository;
import com.backend.eventsrus.repository.UserRepository;
import com.backend.eventsrus.repository.VendorProfileRepository;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CalendarService {

    private final BookingRepository bookingRepository;
    private final ConversationRepository conversationRepository;
    private final EventRepository eventRepository;
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
            if (booking.getStatus() != BookingStatus.APPROVED) {
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

        for (Booking booking : bookingRepository.findByVendorUserIdOrderByEventDatetimeAsc(vendor.getId())) {
            if (booking.getStatus() == BookingStatus.APPROVED) {
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
