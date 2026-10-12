package com.backend.eventsrus.service;

import com.backend.eventsrus.dto.CategoryRecommendationResponse;
import com.backend.eventsrus.enums.BusinessType;
import com.backend.eventsrus.enums.EventType;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Essential-vs-optional BusinessType categorization per EventType - the one
 * shared source EventService#buildSuggestions reads for each
 * SuggestedVendorResponse's "essential" flag, also exposed directly via
 * RecommendationController for a caller that just wants the matrix without
 * creating/loading an event. Precomputed once at class-load time into an
 * EnumMap, so #getRecommendations is an O(1) lookup, not recomputed per call.
 */
@Service
public class SupplierRecommendationService {

    // Essential-category sets per event "family" - every other BusinessType
    // is optional for that family (see #buildRecommendations's switch,
    // which assigns one of these five to every EventType constant).
    private static final Set<BusinessType> WEDDING_ESSENTIALS = Set.of(
            BusinessType.VENUE, BusinessType.CATERING, BusinessType.PHOTO_AND_VIDEO, BusinessType.EVENT_COORDINATOR,
            BusinessType.HAIR_AND_MAKEUP, BusinessType.FLORAL_SERVICES, BusinessType.CAKE_AND_PASTRIES,
            BusinessType.BRIDAL_GOWN_DESIGNER, BusinessType.SUIT_RENTALS, BusinessType.DECORATION_PRODUCTION,
            BusinessType.LIGHTS_AND_SOUNDS, BusinessType.ENTERTAINMENT, BusinessType.EVENT_HOST,
            BusinessType.INVITATIONS, BusinessType.PHOTO_BOOTHS, BusinessType.SOUVENIR_GIVEAWAYS,
            BusinessType.WARDROBE_STYLISTS_DRESSERS, BusinessType.TRANSPORT_SHUTTLE_FLEET_SERVICES,
            BusinessType.SPECIAL_EFFECTS);

    private static final Set<BusinessType> PARTY_ESSENTIALS = Set.of(
            BusinessType.VENUE, BusinessType.CATERING, BusinessType.PHOTO_AND_VIDEO, BusinessType.EVENT_HOST,
            BusinessType.CAKE_AND_PASTRIES, BusinessType.DECORATION_PRODUCTION, BusinessType.ENTERTAINMENT,
            BusinessType.PHOTO_BOOTHS, BusinessType.FOOD_CARTS_GRAZING, BusinessType.SOUVENIR_GIVEAWAYS,
            BusinessType.INFLATABLES, BusinessType.MOBILE_PLAYGROUND, BusinessType.ARCADE, BusinessType.PERFORMERS,
            BusinessType.LIGHTS_AND_SOUNDS, BusinessType.INTERACTIVE_BAR_MIXOLOGY_SERVICES,
            BusinessType.INVITATIONS, BusinessType.SPECIAL_EFFECTS);

    private static final Set<BusinessType> MILESTONE_ESSENTIALS = Set.of(
            BusinessType.VENUE, BusinessType.CATERING, BusinessType.PHOTO_AND_VIDEO, BusinessType.EVENT_HOST,
            BusinessType.EVENT_COORDINATOR, BusinessType.CAKE_AND_PASTRIES, BusinessType.DECORATION_PRODUCTION,
            BusinessType.FLORAL_SERVICES, BusinessType.ENTERTAINMENT, BusinessType.PHOTO_BOOTHS,
            BusinessType.HAIR_AND_MAKEUP, BusinessType.LIGHTS_AND_SOUNDS, BusinessType.FOOD_CARTS_GRAZING,
            BusinessType.INTERACTIVE_BAR_MIXOLOGY_SERVICES, BusinessType.SOUVENIR_GIVEAWAYS,
            BusinessType.INVITATIONS);

    private static final Set<BusinessType> CORPORATE_ESSENTIALS = Set.of(
            BusinessType.VENUE, BusinessType.CATERING, BusinessType.LIGHTS_AND_SOUNDS,
            BusinessType.LED_WALL_VISUAL_PROJECTION_RENTALS, BusinessType.STAGING_TRUSSING_FLOORING_RENTALS,
            BusinessType.EVENT_HOST, BusinessType.EVENT_COORDINATOR, BusinessType.PHOTO_AND_VIDEO,
            BusinessType.ENTERTAINMENT, BusinessType.PERFORMERS, BusinessType.DECORATION_PRODUCTION,
            BusinessType.POWER_GENERATOR_SERVICES, BusinessType.SECURITY_CROWD_CONTROL,
            BusinessType.TRANSPORT_SHUTTLE_FLEET_SERVICES, BusinessType.INTERACTIVE_BAR_MIXOLOGY_SERVICES,
            BusinessType.LIVE_EVENT_PAINTERS_SKETCH_ARTISTS, BusinessType.SPECIAL_EFFECTS,
            BusinessType.INVITATIONS, BusinessType.SOUVENIR_GIVEAWAYS);

    private static final Set<BusinessType> PRODUCTION_ESSENTIALS = Set.of(
            BusinessType.VENUE, BusinessType.LIGHTS_AND_SOUNDS, BusinessType.STAGING_TRUSSING_FLOORING_RENTALS,
            BusinessType.LED_WALL_VISUAL_PROJECTION_RENTALS, BusinessType.POWER_GENERATOR_SERVICES,
            BusinessType.SECURITY_CROWD_CONTROL, BusinessType.PERFORMERS, BusinessType.ENTERTAINMENT,
            BusinessType.EVENT_HOST, BusinessType.FOOD_CARTS_GRAZING, BusinessType.CATERING,
            BusinessType.TRANSPORT_SHUTTLE_FLEET_SERVICES, BusinessType.SPECIAL_EFFECTS,
            BusinessType.PHOTO_AND_VIDEO, BusinessType.INTERACTIVE_BAR_MIXOLOGY_SERVICES);

    private static final Map<EventType, CategoryRecommendationResponse> RECOMMENDATIONS_BY_EVENT_TYPE =
            buildRecommendations();

    private static Map<EventType, CategoryRecommendationResponse> buildRecommendations() {
        Map<EventType, CategoryRecommendationResponse> map = new EnumMap<>(EventType.class);
        for (EventType eventType : EventType.values()) {
            Set<BusinessType> essential = switch (eventType) {
                case WEDDING -> WEDDING_ESSENTIALS;
                case BIRTHDAY, DEBUT, PARTY, BABY_SHOWER -> PARTY_ESSENTIALS;
                case SEMINAR, NETWORKING_EVENT, CORPORATE_EVENT, PRODUCT_LAUNCH, TEAM_BUILDING, CORPORATE_RETREAT,
                        TRADE_SHOW, GALA, FUNDRAISER, EXHIBITION -> CORPORATE_ESSENTIALS;
                case CONCERT, FESTIVAL, SPORTS_EVENT -> PRODUCTION_ESSENTIALS;
                // ANNIVERSARY, BRIDAL_SHOWER, BACHELOR_PARTY, BACHELORETTE_PARTY,
                // ENGAGEMENT_PARTY, GRADUATION, RETIREMENT, REUNION, HOUSEWARMING,
                // OTHER - no dedicated family, treated as a general milestone event.
                default -> MILESTONE_ESSENTIALS;
            };
            Set<BusinessType> optional = EnumSet.allOf(BusinessType.class);
            optional.removeAll(essential);
            map.put(eventType, new CategoryRecommendationResponse(essential, Set.copyOf(optional)));
        }
        return map;
    }

    /**
     * Essential/optional BusinessType categorization for one EventType -
     * O(1), served from the precomputed map above. Every real EventType
     * constant is already covered by #buildRecommendations, so the
     * EventType.OTHER fallback only guards a null input.
     */
    public CategoryRecommendationResponse getRecommendations(EventType eventType) {
        EventType key = eventType == null ? EventType.OTHER : eventType;
        return RECOMMENDATIONS_BY_EVENT_TYPE.get(key);
    }
}
