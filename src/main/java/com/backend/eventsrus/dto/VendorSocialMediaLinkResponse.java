package com.backend.eventsrus.dto;

import com.backend.eventsrus.enums.SocialMediaPlatform;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class VendorSocialMediaLinkResponse {

    private Long id;
    private SocialMediaPlatform platform;
    private String url;
}
