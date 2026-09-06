package com.backend.eventsrus.controller;

import com.backend.eventsrus.dto.LeadResponse;
import com.backend.eventsrus.service.LeadService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/vendors/me/leads")
@RequiredArgsConstructor
public class LeadController {

    private final LeadService leadService;

    @GetMapping
    public List<LeadResponse> listLeads(Authentication authentication) {
        return leadService.listLeadsForVendor(authentication.getName());
    }
}
