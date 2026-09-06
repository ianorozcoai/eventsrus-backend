package com.backend.eventsrus.service;

import com.backend.eventsrus.enums.BusinessType;
import com.backend.eventsrus.enums.EventType;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Placeholder implementation of {@link AiSuggestionService} — templated
 * text + a default vendor-category list per event type, with a light
 * keyword scan over the planner's description to pull in extra categories.
 * Not a real LLM call; swap this out once one is wired up.
 */
@Service
public class RuleBasedAiSuggestionService implements AiSuggestionService {

    private static final Map<EventType, List<BusinessType>> DEFAULT_VENDOR_TYPES = new EnumMap<>(EventType.class);
    private static final Map<EventType, String> IDEA_TEMPLATES = new EnumMap<>(EventType.class);

    static {
        DEFAULT_VENDOR_TYPES.put(EventType.WEDDING,
                List.of(BusinessType.VENUE, BusinessType.CATERING, BusinessType.PHOTO_AND_VIDEO,
                        BusinessType.DECORATION_PRODUCTION, BusinessType.ENTERTAINMENT));
        DEFAULT_VENDOR_TYPES.put(EventType.ANNIVERSARY,
                List.of(BusinessType.VENUE, BusinessType.CATERING, BusinessType.PHOTO_AND_VIDEO,
                        BusinessType.DECORATION_PRODUCTION));
        DEFAULT_VENDOR_TYPES.put(EventType.BIRTHDAY,
                List.of(BusinessType.VENUE, BusinessType.CATERING, BusinessType.ENTERTAINMENT,
                        BusinessType.DECORATION_PRODUCTION));
        DEFAULT_VENDOR_TYPES.put(EventType.PARTY,
                List.of(BusinessType.VENUE, BusinessType.CATERING, BusinessType.ENTERTAINMENT));
        DEFAULT_VENDOR_TYPES.put(EventType.OTHER,
                List.of(BusinessType.VENUE, BusinessType.CATERING));

        IDEA_TEMPLATES.put(EventType.WEDDING,
                "For a memorable wedding, start by locking in your venue and date, then layer in "
                        + "catering, photography, and decor around a single cohesive theme. Book your "
                        + "highest-demand vendors (venue and photographer) first — they tend to fill up "
                        + "fastest for popular dates.");
        IDEA_TEMPLATES.put(EventType.ANNIVERSARY,
                "Anniversaries work best with an intimate, personal touch — a venue that fits your "
                        + "guest count comfortably, catering that reflects a shared favorite, and a "
                        + "photographer to capture the milestone.");
        IDEA_TEMPLATES.put(EventType.BIRTHDAY,
                "Birthdays are all about the guest of honor — pick a venue and entertainment that "
                        + "match their personality, and build catering and decor around that theme.");
        IDEA_TEMPLATES.put(EventType.PARTY,
                "Keep it simple: a flexible venue, crowd-pleasing catering, and entertainment that "
                        + "matches your guest list's energy.");
        IDEA_TEMPLATES.put(EventType.OTHER,
                "Start with a venue that fits your guest count and budget, then build out catering "
                        + "and any specialty vendors your event needs.");
    }

    @Override
    public AiSuggestionResult generateSuggestions(EventType eventType, String description) {
        Set<BusinessType> vendorTypes = new LinkedHashSet<>(
                DEFAULT_VENDOR_TYPES.getOrDefault(eventType, DEFAULT_VENDOR_TYPES.get(EventType.OTHER)));
        vendorTypes.addAll(scanDescriptionForVendorTypes(description));

        String ideaText = IDEA_TEMPLATES.getOrDefault(eventType, IDEA_TEMPLATES.get(EventType.OTHER));

        return new AiSuggestionResult(ideaText, List.copyOf(vendorTypes));
    }

    private Set<BusinessType> scanDescriptionForVendorTypes(String description) {
        Set<BusinessType> found = new LinkedHashSet<>();
        if (description == null || description.isBlank()) {
            return found;
        }

        String lower = description.toLowerCase(Locale.ROOT);
        if (lower.contains("photo") || lower.contains("video")) found.add(BusinessType.PHOTO_AND_VIDEO);
        if (lower.contains("cater") || lower.contains("food") || lower.contains("dining")) found.add(BusinessType.CATERING);
        if (lower.contains("decor")) found.add(BusinessType.DECORATION_PRODUCTION);
        if (lower.contains("flowers") || lower.contains("florist")) found.add(BusinessType.FLORAL_SERVICES);
        if (lower.contains("band") || lower.contains("dj") || lower.contains("music") || lower.contains("entertain")) {
            found.add(BusinessType.ENTERTAINMENT);
        }
        if (lower.contains("venue") || lower.contains("hall") || lower.contains("garden") || lower.contains("outdoor")) {
            found.add(BusinessType.VENUE);
        }
        return found;
    }
}
