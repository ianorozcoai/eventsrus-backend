package com.backend.eventsrus.dto;

import com.backend.eventsrus.enums.BusinessType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SetBookmarkRequest {

    private boolean bookmarked;

    /** The category the planner was browsing when they bookmarked - optional, falls back to the vendor's own first business type. */
    private BusinessType category;
}
