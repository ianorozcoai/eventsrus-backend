package com.backend.eventsrus.model;

import com.backend.eventsrus.common.BaseEntity;
import com.backend.eventsrus.enums.PackagePricingType;
import com.backend.eventsrus.enums.PackageType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "vendor_packages")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class VendorPackage extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_profile_id", nullable = false)
    private VendorProfile vendorProfile;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    // Used when pricingType == FIXED.
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(name = "package_type", nullable = false)
    private PackageType packageType;

    // How this package's price is shown on the storefront - a single
    // amount (price, above), a min-max range (minPrice/maxPrice, below),
    // or no amount at all ("Request for Quotation").
    @Enumerated(EnumType.STRING)
    @Column(name = "pricing_type", nullable = false)
    @Builder.Default
    private PackagePricingType pricingType = PackagePricingType.FIXED;

    // Used when pricingType == RANGE.
    @Column(name = "min_price")
    private BigDecimal minPrice;

    @Column(name = "max_price")
    private BigDecimal maxPrice;

    @Column(nullable = false)
    private boolean active;

    // Vendor-defined groupings (e.g. "Wedding", "Birthday") - a package can
    // belong to several, same shape as VendorPackageImage#tags. Surfaced on
    // the storefront as filter tabs - see VendorDirectoryService.
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "vendor_package_group_memberships",
            joinColumns = @JoinColumn(name = "package_id"),
            inverseJoinColumns = @JoinColumn(name = "group_id"))
    @Builder.Default
    private Set<VendorPackageGroup> groups = new HashSet<>();
}
