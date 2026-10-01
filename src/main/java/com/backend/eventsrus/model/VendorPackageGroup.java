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
 * A vendor-defined label a vendor can attach to any of their packages (see
 * VendorPackage#groups, the join table this is the target of). Deleting a
 * group only removes those associations (DB cascade); the packages
 * themselves are untouched. Surfaced on the public storefront as filter
 * tabs - see VendorDirectoryService#getPublicProfile. Same shape as
 * VendorImageTag, deliberately kept as its own entity/table rather than
 * reused - packages and images are tagged independently of each other.
 */
@Entity
@Table(name = "vendor_package_groups")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class VendorPackageGroup extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_profile_id", nullable = false)
    private VendorProfile vendorProfile;

    @Column(nullable = false)
    private String name;
}
