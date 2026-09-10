package com.backend.eventsrus.service;

import com.backend.eventsrus.dto.ChecklistItemResponse;
import com.backend.eventsrus.dto.CreateEventRequest;
import com.backend.eventsrus.dto.EventResponse;
import com.backend.eventsrus.dto.EventSummaryResponse;
import com.backend.eventsrus.dto.SuggestedVendorResponse;
import com.backend.eventsrus.enums.BusinessType;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
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
    private final ReviewService reviewService;

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

        // Which vendor CATEGORIES this event needs is a one-shot AI decision
        // at creation time, recorded here as one placeholder row per type
        // (vendorProfile always null - that field is unused now). WHICH real
        // vendors currently match those categories is computed live on every
        // read instead (see #buildSuggestions) - location/date can be added
        // or changed later (#updateEventDetails), and matching always
        // reflects whatever's on the event right now rather than a stale
        // snapshot from creation time.
        AiSuggestionService.AiSuggestionResult suggestions =
                aiSuggestionService.generateSuggestions(request.getEventType(), request.getDescription());
        event.setAiIdeaText(suggestions.ideaText());
        eventRepository.save(event);

        for (var vendorType : suggestions.suggestedVendorTypes()) {
            eventSuggestedVendorRepository.save(EventSuggestedVendor.builder()
                    .event(event)
                    .vendorType(vendorType)
                    .build());
        }

        return toResponse(event);
    }

    /**
     * Sets/changes an event's date and/or location after creation - there's
     * no other way to supply these once past the initial intake form.
     * Nothing else needs updating here: vendor matching is computed live
     * from the event's current fields on every read, not stored, so it's
     * automatically fresh the next time this event is fetched.
     */
    @Transactional
    public EventResponse updateEventDetails(String plannerEmail, Long eventId, LocalDate eventDate, String location) {
        Event event = requireOwnedEvent(plannerEmail, eventId);
        event.setEventDate(eventDate);
        event.setLocation(location);
        eventRepository.save(event);
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
        List<SuggestedVendorResponse> suggestions = buildSuggestions(event);

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

    /**
     * Live vendor matches for EVERY business type, computed fresh each call
     * against the event's current location/date rather than read from a
     * stored snapshot. Every type gets a header in the planner's Suppliers
     * list; types with no current match get a single placeholder entry
     * (vendorProfileId null) so the caller can render "No Matching Vendors"
     * under them.
     */
    private List<SuggestedVendorResponse> buildSuggestions(Event event) {
        List<SuggestedVendorResponse> suggestions = new ArrayList<>();
        for (BusinessType type : BusinessType.values()) {
            List<VendorProfile> matches = vendorSearchService.findMatchingVendors(
                    type, event.getLocation(), event.getEventDate(), event.getEventType());
            if (matches.isEmpty()) {
                suggestions.add(SuggestedVendorResponse.builder().vendorType(type).build());
            } else {
                List<SuggestedVendorResponse> forType = new ArrayList<>();
                for (VendorProfile match : matches) {
                    ReviewService.RatingSummary ratings = reviewService.ratingSummary(match.getUser().getId());
                    forType.add(SuggestedVendorResponse.builder()
                            .vendorType(type)
                            .vendorProfileId(match.getId())
                            .businessName(match.getBusinessName())
                            .slug(match.getSlug())
                            .logoImageUrl(match.getLogoImageUrl())
                            .city(match.getCity())
                            .verified(match.isVerified())
                            .topVendor(match.isTopVendor())
                            .averageRating(ratings.averageRating())
                            .reviewCount(ratings.reviewCount())
                            .build());
                }
                // Top Vendors first, then verified, then highest-rated - the
                // spotlight/trusted/best-reviewed rise to the top of their category.
                forType.sort(Comparator
                        .comparing(SuggestedVendorResponse::isTopVendor)
                        .thenComparing(SuggestedVendorResponse::isVerified)
                        .thenComparing(r -> r.getAverageRating() == null ? -1.0 : r.getAverageRating())
                        .reversed());
                suggestions.addAll(forType);
            }
        }
        return suggestions;
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
