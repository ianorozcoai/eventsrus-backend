package com.backend.eventsrus.model;

import com.backend.eventsrus.common.BaseEntity;
import com.backend.eventsrus.enums.PaymentMethodStatus;
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
 * A vendor-uploaded way for planners to pay them directly (a QR code for
 * GCash, Maya, a bank transfer, etc.), shown on the storefront once
 * APPROVED. Admin approval is disabled for now (no review screen exists
 * yet) - VendorPaymentMethodService#create auto-approves every new upload,
 * so this is effectively always APPROVED today. PENDING/REJECTED are kept
 * for when real admin review ships, not removed.
 */
@Entity
@Table(name = "vendor_payment_methods")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class VendorPaymentMethod extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_profile_id", nullable = false)
    private VendorProfile vendorProfile;

    @Column(nullable = false)
    private String label;

    @Column(name = "qr_image_url", nullable = false)
    private String qrImageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethodStatus status;
}
