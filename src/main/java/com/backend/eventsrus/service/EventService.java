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
import com.backend.eventsrus.model.EventBookmarkedVendor;
import com.backend.eventsrus.model.EventChecklistItem;
import com.backend.eventsrus.model.EventSuggestedVendor;
import com.backend.eventsrus.model.User;
import com.backend.eventsrus.model.VendorProfile;
import com.backend.eventsrus.repository.EventBookmarkedVendorRepository;
import com.backend.eventsrus.repository.EventChecklistItemRepository;
import com.backend.eventsrus.repository.EventRepository;
import com.backend.eventsrus.repository.EventSuggestedVendorRepository;
import com.backend.eventsrus.repository.UserRepository;
import com.backend.eventsrus.repository.VendorProfileRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
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
    private final EventBookmarkedVendorRepository eventBookmarkedVendorRepository;
    private final VendorProfileRepository vendorProfileRepository;
    private final UserRepository userRepository;
    private final AiSuggestionService aiSuggestionService;
    private final VendorSearchService vendorSearchService;
    private final ReviewService reviewService;
    private final SupplierRecommendationService supplierRecommendationService;

    @Transactional
    public EventResponse createEvent(String plannerEmail, CreateEventRequest request) {
        User planner = requireUser(plannerEmail);

        Event event = eventRepository.save(Event.builder()
                .planner(planner)
                .name(request.getName())
                .eventType(request.getEventType())
                .eventDate(request.getEventDate())
                .location(request.getLocation())
                .description(request.getDescription())
                .budget(request.getBudget())
                .guestCount(request.getGuestCount())
                .saved(true)
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
     * One checklist item per category the planner actively selects ("Book a
     * catering vendor", ...) - created the moment a category is first
     * checked (see #setSupplierTypeSelection), not seeded in bulk at
     * creation time the way it used to be. Idempotent by label so
     * re-checking a category after unchecking it doesn't duplicate the
     * item - unchecking deliberately never removes it, so any progress
     * already marked done is never silently lost.
     */
    private void addSupplierCategoryChecklistItem(Event event, BusinessType vendorType) {
        String label = "Book a " + vendorType.name().toLowerCase().replace('_', ' ') + " vendor";
        boolean alreadyExists = eventChecklistItemRepository.findByEventIdOrderByCreatedAtAsc(event.getId()).stream()
                .anyMatch(item -> item.getLabel().equals(label));
        if (alreadyExists) {
            return;
        }
        eventChecklistItemRepository.save(EventChecklistItem.builder()
                .event(event)
                .label(label)
                .status(ChecklistStatus.TODO)
                .source(ChecklistSource.SYSTEM_SUGGESTED)
                .vendorType(vendorType)
                .build());
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

    /**
     * Kept for renaming an existing event (or finishing a legacy
     * pre-mandatory-name event created before this field was required) -
     * no longer part of the primary creation flow.
     */
    @Transactional
    public EventResponse saveEvent(String plannerEmail, Long eventId, String name) {
        Event event = requireOwnedEvent(plannerEmail, eventId);
        event.setName(name);
        event.setSaved(true);
        eventRepository.save(event);
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

        List<Long> bookmarkedVendorProfileIds = eventBookmarkedVendorRepository.findByEventId(event.getId()).stream()
                .map(b -> b.getVendorProfile().getId())
                .toList();

        return EventResponse.builder()
                .id(event.getId())
                .name(event.getName())
                .eventType(event.getEventType())
                .eventDate(event.getEventDate())
                .location(event.getLocation())
                .description(event.getDescription())
                .budget(event.getBudget())
                .guestCount(event.getGuestCount())
                .aiIdeaText(event.getAiIdeaText())
                .saved(event.isSaved())
                .suggestions(suggestions)
                .checklist(checklist)
                .bookmarkedVendorProfileIds(bookmarkedVendorProfileIds)
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
        Set<BusinessType> essentialTypes = supplierRecommendationService.getRecommendations(event.getEventType()).essential();
        for (BusinessType type : BusinessType.values()) {
            EventSuggestedVendor categoryState = eventSuggestedVendorRepository
                    .findByEventIdAndVendorType(event.getId(), type)
                    .orElse(null);
            boolean categorySelected = categoryState != null && categoryState.isSelected();
            var categoryBudget = categoryState == null ? null : categoryState.getBudget();
            boolean essential = essentialTypes.contains(type);

            List<VendorProfile> matches = vendorSearchService.findMatchingVendors(
                    type, event.getLocation(), event.getEventDate(), event.getEventType());
            if (matches.isEmpty()) {
                suggestions.add(SuggestedVendorResponse.builder()
                        .vendorType(type)
                        .categorySelected(categorySelected)
                        .categoryBudget(categoryBudget)
                        .essential(essential)
                        .build());
            } else {
                List<SuggestedVendorResponse> forType = new ArrayList<>();
                for (VendorProfile match : matches) {
                    ReviewService.RatingSummary ratings = reviewService.ratingSummary(match.getUser().getId());
                    forType.add(SuggestedVendorResponse.builder()
                            .vendorType(type)
                            .categorySelected(categorySelected)
                            .categoryBudget(categoryBudget)
                            .essential(essential)
                            .vendorProfileId(match.getId())
                            .businessName(match.getBusinessName())
                            // List.copyOf forces this LAZY collection to materialize right
                            // now, while still inside this method's transaction - passing
                            // the raw Hibernate-backed list through isn't enough, since
                            // Jackson only actually reads it later, during HTTP response
                            // serialization, by which point the session's already closed
                            // (see VendorSearchService#searchVendors for the same fix).
                            .businessTypes(List.copyOf(match.getBusinessTypes()))
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

    /**
     * Opts a suggested category in/out of active browsing, with an optional
     * per-category budget earmark. Creates the EventSuggestedVendor row if
     * this category had no match at suggestion time (vendorProfile null is
     * already the existing meaning for "no live match" - this just reuses
     * the same row shape for "not yet decided" vs "decided"). Checking a
     * category also creates its "Book a X vendor" checklist item, if it
     * doesn't already have one - unchecking deliberately leaves that item
     * alone (see #addSupplierCategoryChecklistItem).
     */
    @Transactional
    public EventResponse setSupplierTypeSelection(
            String plannerEmail, Long eventId, BusinessType vendorType, boolean selected, BigDecimal budget) {
        Event event = requireOwnedEvent(plannerEmail, eventId);
        EventSuggestedVendor categoryState = eventSuggestedVendorRepository
                .findByEventIdAndVendorType(eventId, vendorType)
                .orElseGet(() -> EventSuggestedVendor.builder()
                        .event(event)
                        .vendorType(vendorType)
                        .build());
        categoryState.setSelected(selected);
        categoryState.setBudget(budget);
        eventSuggestedVendorRepository.save(categoryState);
        if (selected) {
            addSupplierCategoryChecklistItem(event, vendorType);
        }
        return toResponse(event);
    }

    /**
     * Removes a category from Search Result outright - distinct from
     * #setSupplierTypeSelection(selected=false), which deliberately leaves
     * checklist items in place (same "unchecking never deletes" reasoning
     * as crossing out one item). This is the planner's one explicit "I
     * don't want this category at all anymore" action, so it also deletes
     * every checklist item under this category - both its own "Book a X
     * vendor" item and any bookmark-origin items grouped under it (see
     * #setVendorBookmark's category-context resolution). Existing
     * bookmarks themselves (event_bookmarked_vendors) are left alone - a
     * planner who re-adds this category later still sees those vendors as
     * bookmarked on their storefronts.
     */
    @Transactional
    public EventResponse removeCategorySelection(String plannerEmail, Long eventId, BusinessType vendorType) {
        Event event = requireOwnedEvent(plannerEmail, eventId);
        eventSuggestedVendorRepository.findByEventIdAndVendorType(eventId, vendorType)
                .ifPresent(categoryState -> {
                    categoryState.setSelected(false);
                    categoryState.setBudget(null);
                    eventSuggestedVendorRepository.save(categoryState);
                });
        eventChecklistItemRepository.deleteByEventIdAndVendorType(eventId, vendorType);
        return toResponse(event);
    }

    private ChecklistItemResponse toChecklistResponse(EventChecklistItem item) {
        VendorProfile vendorProfile = item.getVendorProfile();
        return ChecklistItemResponse.builder()
                .id(item.getId())
                .label(item.getLabel())
                .status(item.getStatus())
                .source(item.getSource())
                .vendorType(item.getVendorType())
                .vendorProfileId(vendorProfile == null ? null : vendorProfile.getId())
                .vendorSlug(vendorProfile == null ? null : vendorProfile.getSlug())
                .build();
    }

    /**
     * Bookmarks (or un-bookmarks) a specific supplier for an event, from
     * that supplier's storefront - addressed by vendorUserId, same as
     * ConversationApi#sendInquiry, since a planner-facing page only ever
     * has the vendor's User id on hand, not their internal VendorProfile
     * id. Bookmarking creates a "X was bookmarked" checklist item (if one
     * doesn't already exist for this vendor); un-bookmarking deliberately
     * leaves that item in place, same reasoning as unchecking a category.
     * Bookmarking also opts the vendor's category into Search Result if it
     * wasn't already selected - a planner who name-searched straight to a
     * specific vendor without ever seeing (or without checking) that
     * category in the create-event wizard still gets it into Search Result
     * and its own "Book a X vendor" checklist item, same as if they'd
     * checked it there.
     */
    @Transactional
    public EventResponse setVendorBookmark(
            String plannerEmail, Long eventId, Long vendorUserId, boolean bookmarked, BusinessType category) {
        Event event = requireOwnedEvent(plannerEmail, eventId);
        VendorProfile vendorProfile = vendorProfileRepository.findByUserId(vendorUserId)
                .orElseThrow(() -> new IllegalStateException("Vendor not found: " + vendorUserId));

        if (bookmarked) {
            if (eventBookmarkedVendorRepository.findByEventIdAndVendorProfileId(eventId, vendorProfile.getId()).isEmpty()) {
                eventBookmarkedVendorRepository.save(EventBookmarkedVendor.builder()
                        .event(event)
                        .vendorProfile(vendorProfile)
                        .build());
            }
            // The category the planner was browsing when they bookmarked
            // (passed from the Browse modal/screen's storefront link) - lets
            // the Checklist group this item under the right category
            // heading. Falls back to the vendor's first business type when
            // bookmarked straight from a name search, with no category
            // context on hand.
            BusinessType resolvedCategory = category != null
                    ? category
                    : vendorProfile.getBusinessTypes().isEmpty() ? null : vendorProfile.getBusinessTypes().get(0);
            if (resolvedCategory != null) {
                EventSuggestedVendor categoryState = eventSuggestedVendorRepository
                        .findByEventIdAndVendorType(eventId, resolvedCategory)
                        .orElseGet(() -> EventSuggestedVendor.builder()
                                .event(event)
                                .vendorType(resolvedCategory)
                                .build());
                if (!categoryState.isSelected()) {
                    categoryState.setSelected(true);
                    eventSuggestedVendorRepository.save(categoryState);
                    addSupplierCategoryChecklistItem(event, resolvedCategory);
                }
            }
            addBookmarkChecklistItem(event, vendorProfile, resolvedCategory);
        } else {
            eventBookmarkedVendorRepository.deleteByEventIdAndVendorProfileId(eventId, vendorProfile.getId());
        }
        return toResponse(event);
    }

    private void addBookmarkChecklistItem(Event event, VendorProfile vendorProfile, BusinessType category) {
        boolean alreadyExists = eventChecklistItemRepository.findByEventIdOrderByCreatedAtAsc(event.getId()).stream()
                .anyMatch(item -> item.getVendorProfile() != null
                        && item.getVendorProfile().getId().equals(vendorProfile.getId()));
        if (alreadyExists) {
            return;
        }
        eventChecklistItemRepository.save(EventChecklistItem.builder()
                .event(event)
                .label(vendorProfile.getBusinessName() + " was bookmarked")
                .status(ChecklistStatus.TODO)
                .source(ChecklistSource.SYSTEM_SUGGESTED)
                .vendorProfile(vendorProfile)
                .vendorType(category)
                .build());
    }
}
