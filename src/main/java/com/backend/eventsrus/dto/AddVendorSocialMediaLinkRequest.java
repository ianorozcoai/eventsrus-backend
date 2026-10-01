package com.backend.eventsrus.dto;

import com.backend.eventsrus.enums.SocialMediaPlatform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AddVendorSocialMediaLinkRequest {

    @NotNull
    private SocialMediaPlatform platform;

    @NotBlank
    private String url;
}
