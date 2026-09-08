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
 * One photo attached to a package - shown on the package card in the
 * vendor's own Manage Packages (edit mode) and, combined across every
 * package a vendor has, as the storefront's Gallery section (see
 * VendorPackageImageService, VendorDirectoryService#getPublicProfile).
 * Public bucket, direct URL (imageUrl, not a key to presign) - marketing
 * photos, same treatment as the vendor logo, not a privacy-sensitive
 * document like VendorLegalDocument's uploads.
 */
@Entity
@Table(name = "vendor_package_images")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class VendorPackageImage extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_package_id", nullable = false)
    private VendorPackage vendorPackage;

    @Column(name = "image_url", nullable = false)
    private String imageUrl;

    /** Free-text, optional - e.g. "Setup at a 150-guest wedding". */
    private String caption;
}
