package com.backend.eventsrus.controller;

import com.backend.eventsrus.dto.CategoryRecommendationResponse;
import com.backend.eventsrus.enums.EventType;
import com.backend.eventsrus.service.SupplierRecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

    private final SupplierRecommendationService supplierRecommendationService;

    /** Essential/optional BusinessType categorization for one EventType - same matrix EventService#buildSuggestions uses. */
    @GetMapping
    public CategoryRecommendationResponse getRecommendations(@RequestParam EventType eventType) {
        return supplierRecommendationService.getRecommendations(eventType);
    }
}
