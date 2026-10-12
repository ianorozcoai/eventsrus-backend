package com.backend.eventsrus.controller;

import com.backend.eventsrus.dto.ChecklistItemRequest;
import com.backend.eventsrus.dto.ChecklistItemResponse;
import com.backend.eventsrus.dto.CreateEventRequest;
import com.backend.eventsrus.dto.EventResponse;
import com.backend.eventsrus.dto.EventSummaryResponse;
import com.backend.eventsrus.dto.SaveEventRequest;
import com.backend.eventsrus.dto.SetBookmarkRequest;
import com.backend.eventsrus.dto.UpdateChecklistStatusRequest;
import com.backend.eventsrus.dto.UpdateEventDetailsRequest;
import com.backend.eventsrus.dto.UpdateSupplierTypeSelectionRequest;
import com.backend.eventsrus.enums.BusinessType;
import com.backend.eventsrus.service.EventService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    @PostMapping
    public EventResponse createEvent(@Valid @RequestBody CreateEventRequest request, Authentication authentication) {
        return eventService.createEvent(authentication.getName(), request);
    }

    @GetMapping
    public List<EventSummaryResponse> listEvents(Authentication authentication) {
        return eventService.listEvents(authentication.getName());
    }

    @GetMapping("/{eventId}")
    public EventResponse getEvent(@PathVariable Long eventId, Authentication authentication) {
        return eventService.getEvent(authentication.getName(), eventId);
    }

    @PutMapping("/{eventId}/save")
    public EventResponse saveEvent(
            @PathVariable Long eventId, @Valid @RequestBody SaveEventRequest request, Authentication authentication) {
        return eventService.saveEvent(authentication.getName(), eventId, request.getName());
    }

    /**
     * Sets/changes date and/or location after creation - the only way to
     * supply these once past the initial intake form (both are optional
     * there). Returns the event with vendor suggestions freshly matched
     * against whatever's now on it - see EventService#buildSuggestions.
     */
    @PutMapping("/{eventId}/details")
    public EventResponse updateEventDetails(
            @PathVariable Long eventId, @RequestBody UpdateEventDetailsRequest request, Authentication authentication) {
        return eventService.updateEventDetails(
                authentication.getName(), eventId, request.getEventDate(), request.getLocation());
    }

    /**
     * Opts a suggested supplier category in/out of active browsing, with an
     * optional per-category budget earmark. Returns the event with
     * suggestions freshly matched, same as #updateEventDetails.
     */
    @PutMapping("/{eventId}/supplier-types/{vendorType}")
    public EventResponse setSupplierTypeSelection(
            @PathVariable Long eventId,
            @PathVariable BusinessType vendorType,
            @Valid @RequestBody UpdateSupplierTypeSelectionRequest request,
            Authentication authentication) {
        return eventService.setSupplierTypeSelection(
                authentication.getName(), eventId, vendorType, request.isSelected(), request.getBudget());
    }

    /**
     * Removes a category from Search Result outright, deleting its
     * checklist items too - see EventService#removeCategorySelection for
     * why this is a separate, more drastic action than
     * #setSupplierTypeSelection(selected=false).
     */
    @DeleteMapping("/{eventId}/supplier-types/{vendorType}")
    public EventResponse removeCategorySelection(
            @PathVariable Long eventId, @PathVariable BusinessType vendorType, Authentication authentication) {
        return eventService.removeCategorySelection(authentication.getName(), eventId, vendorType);
    }

    /**
     * Bookmarks (or un-bookmarks) a specific supplier for an event, from
     * that supplier's storefront - addressed by vendorUserId (the only
     * vendor identifier a storefront page has on hand, same as
     * ConversationApi#sendInquiry). Creates a "X was bookmarked" checklist
     * item on bookmark, grouped under request.category (the category the
     * planner was browsing) so the Checklist can show it under that
     * category's heading.
     */
    @PutMapping("/{eventId}/bookmarks/{vendorUserId}")
    public EventResponse setVendorBookmark(
            @PathVariable Long eventId,
            @PathVariable Long vendorUserId,
            @Valid @RequestBody SetBookmarkRequest request,
            Authentication authentication) {
        return eventService.setVendorBookmark(
                authentication.getName(), eventId, vendorUserId, request.isBookmarked(), request.getCategory());
    }

    @PostMapping("/{eventId}/checklist")
    public ChecklistItemResponse addChecklistItem(
            @PathVariable Long eventId, @Valid @RequestBody ChecklistItemRequest request, Authentication authentication) {
        return eventService.addChecklistItem(authentication.getName(), eventId, request.getLabel());
    }

    @PutMapping("/{eventId}/checklist/{itemId}")
    public ChecklistItemResponse updateChecklistItemStatus(
            @PathVariable Long eventId,
            @PathVariable Long itemId,
            @Valid @RequestBody UpdateChecklistStatusRequest request,
            Authentication authentication) {
        return eventService.updateChecklistItemStatus(authentication.getName(), eventId, itemId, request.getStatus());
    }

}
