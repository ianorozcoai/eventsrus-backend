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
 * One standalone storefront photo, not attached to any package - lets a
 * vendor showcase sample work even when their packages themselves don't
 * have much to hang photos off of. Combined with every package's own
 * VendorPackageImage rows to build the storefront's single Gallery section
 * (see VendorGalleryPhotoService, VendorDirectoryService#getPublicProfile).
 * Same public-bucket, direct-URL treatment as VendorPackageImage - these are
 * marketing photos, not a privacy-sensitive document.
 */
@Entity
@Table(name = "vendor_gallery_photos")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class VendorGalleryPhoto extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_profile_id", nullable = false)
    private VendorProfile vendorProfile;

    @Column(name = "image_url", nullable = false)
    private String imageUrl;

    /** Free-text, optional - e.g. "Setup at a 150-guest wedding". */
    private String caption;
}
