package com.backend.eventsrus.service;

import com.backend.eventsrus.dto.ChecklistItemResponse;
import com.backend.eventsrus.dto.CreateEventRequest;
import com.backend.eventsrus.dto.EventResponse;
import com.backend.eventsrus.dto.EventSummaryResponse;
import com.backend.eventsrus.dto.SuggestedVendorResponse;
import com.backend.eventsrus.enums.ChecklistSource;
import com.backend.eventsrus.enums.ChecklistStatus;
import com.backend.eventsrus.model.Event;
import com.backend.eventsrus.model.EventChecklistItem;
import com.backend.eventsrus.model.EventSuggestedVendor;
import com.backend.eventsrus.model.User;
import com.backend.eventsrus.model.VendorProfile;
import com.backend.eventsrus.repository.EventChecklistItemRepository;
import com.backend.eventsrus.repository.EventRepository;
import com.backend.eventsrus.repository.EventSuggestedVendorRepository;
import com.backend.eventsrus.repository.UserRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
    private final EventSuggestedVendorRepository eventSuggestedVendorRepository;
    private final EventChecklistItemRepository eventChecklistItemRepository;
    private final UserRepository userRepository;
    private final AiSuggestionService aiSuggestionService;
    private final VendorSearchService vendorSearchService;

    @Transactional
    public EventResponse createEvent(String plannerEmail, CreateEventRequest request) {
        User planner = requireUser(plannerEmail);

        Event event = eventRepository.save(Event.builder()
                .planner(planner)
                .eventType(request.getEventType())
                .eventDate(request.getEventDate())
                .location(request.getLocation())
                .description(request.getDescription())
                .saved(false)
                .build());

        AiSuggestionService.AiSuggestionResult suggestions =
                aiSuggestionService.generateSuggestions(request.getEventType(), request.getDescription());
        event.setAiIdeaText(suggestions.ideaText());
        eventRepository.save(event);

        for (var vendorType : suggestions.suggestedVendorTypes()) {
            List<VendorProfile> matches = vendorSearchService.findMatchingVendors(vendorType, request.getLocation());
            if (matches.isEmpty()) {
                eventSuggestedVendorRepository.save(EventSuggestedVendor.builder()
                        .event(event)
                        .vendorType(vendorType)
                        .build());
            } else {
                for (VendorProfile match : matches) {
                    eventSuggestedVendorRepository.save(EventSuggestedVendor.builder()
                            .event(event)
                            .vendorProfile(match)
                            .vendorType(vendorType)
                            .build());
                }
            }
        }

        return toResponse(event);
    }

    @Transactional
    public EventResponse saveEvent(String plannerEmail, Long eventId, String name) {
        Event event = requireOwnedEvent(plannerEmail, eventId);
        event.setName(name);
        event.setSaved(true);
        eventRepository.save(event);

        if (eventChecklistItemRepository.findByEventIdOrderByCreatedAtAsc(eventId).isEmpty()) {
            Set<String> seeded = new LinkedHashSet<>();
            for (EventSuggestedVendor suggestion : eventSuggestedVendorRepository.findByEventId(eventId)) {
                String label = "Book a " + suggestion.getVendorType().name().toLowerCase().replace('_', ' ') + " vendor";
                if (seeded.add(label)) {
                    eventChecklistItemRepository.save(EventChecklistItem.builder()
                            .event(event)
                            .label(label)
                            .status(ChecklistStatus.TODO)
                            .source(ChecklistSource.SYSTEM_SUGGESTED)
                            .build());
                }
            }
        }

        return toResponse(event);
    }

    @Transactional(readOnly = true)
    public EventResponse getEvent(String plannerEmail, Long eventId) {
        return toResponse(requireOwnedEvent(plannerEmail, eventId));
    }

    public List<EventSummaryResponse> listEvents(String plannerEmail) {
        User planner = requireUser(plannerEmail);
        return eventRepository.findByPlannerIdOrderByCreatedAtDesc(planner.getId()).stream()
                .map(e -> EventSummaryResponse.builder()
                        .id(e.getId())
                        .name(e.getName())
                        .eventType(e.getEventType())
                        .eventDate(e.getEventDate())
                        .saved(e.isSaved())
                        .build())
                .toList();
    }

    @Transactional
    public ChecklistItemResponse addChecklistItem(String plannerEmail, Long eventId, String label) {
        Event event = requireOwnedEvent(plannerEmail, eventId);
        EventChecklistItem item = eventChecklistItemRepository.save(EventChecklistItem.builder()
                .event(event)
                .label(label)
                .status(ChecklistStatus.TODO)
                .source(ChecklistSource.CUSTOM)
                .build());
        return toChecklistResponse(item);
    }

    @Transactional
    public ChecklistItemResponse updateChecklistItemStatus(
            String plannerEmail, Long eventId, Long itemId, ChecklistStatus status) {
        requireOwnedEvent(plannerEmail, eventId);
        EventChecklistItem item = eventChecklistItemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalStateException("Checklist item not found: " + itemId));
        item.setStatus(status);
        eventChecklistItemRepository.save(item);
        return toChecklistResponse(item);
    }

    private Event requireOwnedEvent(String plannerEmail, Long eventId) {
        User planner = requireUser(plannerEmail);
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalStateException("Event not found: " + eventId));
        if (!event.getPlanner().getId().equals(planner.getId())) {
            throw new IllegalStateException("Event does not belong to the authenticated user");
        }
        return event;
    }

    private User requireUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + email));
    }

    private EventResponse toResponse(Event event) {
        List<SuggestedVendorResponse> suggestions = eventSuggestedVendorRepository.findByEventId(event.getId()).stream()
                .map(s -> SuggestedVendorResponse.builder()
                        .vendorType(s.getVendorType())
                        .vendorProfileId(s.getVendorProfile() != null ? s.getVendorProfile().getId() : null)
                        .businessName(s.getVendorProfile() != null ? s.getVendorProfile().getBusinessName() : null)
                        .slug(s.getVendorProfile() != null ? s.getVendorProfile().getSlug() : null)
                        .logoImageUrl(s.getVendorProfile() != null ? s.getVendorProfile().getLogoImageUrl() : null)
                        .city(s.getVendorProfile() != null ? s.getVendorProfile().getCity() : null)
                        .build())
                .toList();

        List<ChecklistItemResponse> checklist = eventChecklistItemRepository.findByEventIdOrderByCreatedAtAsc(event.getId())
                .stream()
                .map(this::toChecklistResponse)
                .toList();

        return EventResponse.builder()
                .id(event.getId())
                .name(event.getName())
                .eventType(event.getEventType())
                .eventDate(event.getEventDate())
                .location(event.getLocation())
                .description(event.getDescription())
                .aiIdeaText(event.getAiIdeaText())
                .saved(event.isSaved())
                .suggestions(suggestions)
                .checklist(checklist)
                .build();
    }

    private ChecklistItemResponse toChecklistResponse(EventChecklistItem item) {
        return ChecklistItemResponse.builder()
                .id(item.getId())
                .label(item.getLabel())
                .status(item.getStatus())
                .source(item.getSource())
                .build();
    }
}
