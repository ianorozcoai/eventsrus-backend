package com.backend.eventsrus.dto;

import java.time.Instant;
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
}
