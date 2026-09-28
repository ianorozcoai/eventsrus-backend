package com.backend.eventsrus.model;

import com.backend.eventsrus.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * A vendor-defined label a vendor can attach to any of their images
 * (standalone gallery photos or package photos - see the two join tables
 * this is the target of, on VendorGalleryPhoto#tags/VendorPackageImage#tags).
 * Deleting a tag only removes those associations (DB cascade); the photos
 * themselves are untouched. Surfaced on the public storefront as filter
 * tabs - see VendorDirectoryService#getPublicProfile.
 */
@Entity
@Table(name = "vendor_image_tags")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class VendorImageTag extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_profile_id", nullable = false)
    private VendorProfile vendorProfile;

    @Column(nullable = false)
    private String name;
}
