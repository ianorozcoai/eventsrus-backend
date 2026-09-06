package com.backend.eventsrus.service;

import com.backend.eventsrus.enums.BusinessType;
import com.backend.eventsrus.enums.EventType;
import java.util.List;

/**
 * Generates the "idea how to execute this event" text plus a list of vendor
 * categories the planner likely needs. {@link RuleBasedAiSuggestionService}
 * is a placeholder heuristic implementation — swap in a real LLM-backed
 * implementation of this interface later without touching any caller.
 */
public interface AiSuggestionService {

    AiSuggestionResult generateSuggestions(EventType eventType, String description);

    record AiSuggestionResult(String ideaText, List<BusinessType> suggestedVendorTypes) {
    }
}
