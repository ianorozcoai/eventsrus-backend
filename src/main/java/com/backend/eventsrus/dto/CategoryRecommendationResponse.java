package com.backend.eventsrus.dto;

import com.backend.eventsrus.enums.BusinessType;
import java.util.Set;

/** Essential (must-have) vs optional (add-on) supplier categories for one EventType - see SupplierRecommendationService. */
public record CategoryRecommendationResponse(Set<BusinessType> essential, Set<BusinessType> optional) {}
