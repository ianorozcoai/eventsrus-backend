package com.backend.eventsrus.controller;

import com.backend.eventsrus.dto.CalendarEntryResponse;
import com.backend.eventsrus.service.CalendarService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CalendarController {

    private final CalendarService calendarService;

    @GetMapping("/api/v1/planners/me/calendar")
    public List<CalendarEntryResponse> plannerCalendar(Authentication authentication) {
        return calendarService.plannerCalendar(authentication.getName());
    }

    @GetMapping("/api/v1/vendors/me/calendar")
    public List<CalendarEntryResponse> vendorCalendar(Authentication authentication) {
        return calendarService.vendorCalendar(authentication.getName());
    }
}
