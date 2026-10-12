package com.backend.eventsrus.dto;

import com.backend.eventsrus.enums.BusinessType;
import com.backend.eventsrus.enums.ChecklistSource;
import com.backend.eventsrus.enums.ChecklistStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class ChecklistItemResponse {

    private Long id;
    private String label;
    private ChecklistStatus status;
    private ChecklistSource source;
    /** Which supplier category this came from, if any - null for a custom or bookmark-origin item. */
    private BusinessType vendorType;
    /** Which specific vendor this came from, if any - set only for a bookmark-origin item, so the client can link straight to that vendor's storefront. */
    private Long vendorProfileId;
    private String vendorSlug;
}
