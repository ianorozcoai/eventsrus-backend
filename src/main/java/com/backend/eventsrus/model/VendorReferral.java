package com.backend.eventsrus.model;

import com.backend.eventsrus.common.BaseEntity;
import com.backend.eventsrus.enums.ReferralStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * One row per successful referral attribution - created the moment a new
 * vendor onboards using another vendor's referral code (see
 * UserService#becomeVendor), and flipped to CONVERTED the first time that
 * referred vendor's subscription is backed by a real PayPal payment (see
 * PayPalWebhookService's PAYMENT.SALE.COMPLETED handling). commissionAmount
 * is snapshotted at conversion time (from the referral-commission-amount
 * System Setting - see SystemSettingService) so
 * a later config change doesn't retroactively change what's already owed.
 * COMMISSION_PAID is set manually by an admin once the payout has actually
 * been sent - there's no automated disbursement.
 */
@Entity
@Table(name = "vendor_referrals")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class VendorReferral extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "referrer_user_id", nullable = false)
    private User referrer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "referred_user_id", nullable = false, unique = true)
    private User referred;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReferralStatus status;

    /** Null until CONVERTED - snapshotted from config at that moment, not looked up live. */
    @Column(name = "commission_amount")
    private BigDecimal commissionAmount;

    @Column(name = "converted_at")
    private Instant convertedAt;

    @Column(name = "paid_at")
    private Instant paidAt;
}
