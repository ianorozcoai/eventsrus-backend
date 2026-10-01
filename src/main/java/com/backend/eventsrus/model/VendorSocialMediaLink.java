package com.backend.eventsrus.model;

import com.backend.eventsrus.common.BaseEntity;
import com.backend.eventsrus.enums.SocialMediaPlatform;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * One social media link a vendor has added (X, Instagram, Facebook, TikTok,
 * or YouTube) - a vendor can have as many as they want, including several of
 * the same platform. Shown on the public storefront - see
 * VendorDirectoryService#getPublicProfile. Ordered by createdAt ascending
 * everywhere it's listed, so the first one a vendor ever added (including
 * one seeded from their onboarding Facebook Page URL - see
 * UserService#becomeVendor/#updateSettings) naturally stays first.
 */
@Entity
@Table(name = "vendor_social_media_links")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class VendorSocialMediaLink extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_profile_id", nullable = false)
    private VendorProfile vendorProfile;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SocialMediaPlatform platform;

    @Column(nullable = false)
    private String url;
}
