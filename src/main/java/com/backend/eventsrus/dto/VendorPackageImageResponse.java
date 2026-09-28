package com.backend.eventsrus.dto;

import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class VendorPackageImageResponse {

    private Long id;
    private String imageUrl;
    private String caption;
    private Instant createdAt;
    /** Vendor-defined tag names on this image, sorted - see VendorImageTagService. */
    private List<String> tags;
}
