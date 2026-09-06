package com.backend.eventsrus.service;

import com.backend.eventsrus.dto.LeadResponse;
import com.backend.eventsrus.enums.NotificationType;
import com.backend.eventsrus.model.Event;
import com.backend.eventsrus.model.Lead;
import com.backend.eventsrus.model.User;
import com.backend.eventsrus.repository.EventRepository;
import com.backend.eventsrus.repository.LeadRepository;
import com.backend.eventsrus.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LeadService {

    private final LeadRepository leadRepository;
    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final NotificationService notificationService;

    /** Called when a planner opens a vendor's page in the context of one of their events. */
    @Transactional
    public void recordVisit(String plannerEmail, Long vendorUserId, Long eventId) {
        User planner = userRepository.findByEmail(plannerEmail)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + plannerEmail));
        User vendor = userRepository.findById(vendorUserId)
                .orElseThrow(() -> new IllegalStateException("Vendor not found: " + vendorUserId));
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalStateException("Event not found: " + eventId));

        Instant now = Instant.now();
        var existing = leadRepository.findByVendorUserIdAndPlannerUserIdAndEventId(vendorUserId, planner.getId(), eventId);
        if (existing.isPresent()) {
            Lead lead = existing.get();
            lead.setLastVisitedAt(now);
            leadRepository.save(lead);
            return;
        }

        leadRepository.save(Lead.builder()
                .vendorUser(vendor)
                .plannerUser(planner)
                .event(event)
                .firstVisitedAt(now)
                .lastVisitedAt(now)
                .build());

        notificationService.notify(vendor, NotificationType.NEW_LEAD,
                "New lead",
                (planner.getFirstName() != null ? planner.getFirstName() : "A planner")
                        + " viewed your storefront for their event",
                "LEAD", null);
    }

    @Transactional(readOnly = true)
    public List<LeadResponse> listLeadsForVendor(String vendorEmail) {
        User vendor = userRepository.findByEmail(vendorEmail)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + vendorEmail));
        return leadRepository.findByVendorUserIdOrderByLastVisitedAtDesc(vendor.getId()).stream()
                .map(lead -> LeadResponse.builder()
                        .id(lead.getId())
                        .plannerUserId(lead.getPlannerUser().getId())
                        .plannerName(displayName(lead.getPlannerUser()))
                        .eventId(lead.getEvent().getId())
                        .eventName(lead.getEvent().getName())
                        .firstVisitedAt(lead.getFirstVisitedAt())
                        .lastVisitedAt(lead.getLastVisitedAt())
                        .build())
                .toList();
    }

    private String displayName(User user) {
        if (user.getFirstName() != null) {
            return user.getLastName() != null ? user.getFirstName() + " " + user.getLastName() : user.getFirstName();
        }
        return user.getEmail();
    }
}
