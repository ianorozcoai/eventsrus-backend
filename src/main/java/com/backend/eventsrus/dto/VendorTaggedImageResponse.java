package com.backend.eventsrus.dto;

import com.backend.eventsrus.enums.ImageSource;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * The vendor-facing shape for the Gallery Management page - unlike
 * VendorPackageImageResponse (the public storefront shape), this knows
 * which underlying row to edit (source + packageId) and carries the
 * package name for context. See VendorImageTagService#listAllTaggableImages.
 */
@Getter
@Builder
@AllArgsConstructor
public class VendorTaggedImageResponse {

    private Long id;
    private String imageUrl;
    private String caption;
    private Instant createdAt;
    private ImageSource source;
    /** Only present when source == PACKAGE. */
    private Long packageId;
    /** Only present when source == PACKAGE. */
    private String packageName;
    private List<VendorImageTagResponse> tags;
}
