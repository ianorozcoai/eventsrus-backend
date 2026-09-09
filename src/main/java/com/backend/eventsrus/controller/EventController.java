package com.backend.eventsrus.controller;

import com.backend.eventsrus.dto.ChecklistItemRequest;
import com.backend.eventsrus.dto.ChecklistItemResponse;
import com.backend.eventsrus.dto.CreateEventRequest;
import com.backend.eventsrus.dto.EventResponse;
import com.backend.eventsrus.dto.EventSummaryResponse;
import com.backend.eventsrus.dto.SaveEventRequest;
import com.backend.eventsrus.dto.UpdateChecklistStatusRequest;
import com.backend.eventsrus.dto.UpdateEventDetailsRequest;
import com.backend.eventsrus.service.EventService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
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
