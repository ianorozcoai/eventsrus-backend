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
 * approved. New uploads always start PENDING - there's no admin review
 * screen yet, so nothing a vendor uploads here is publicly visible until
 * that ships and someone approves it.
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
