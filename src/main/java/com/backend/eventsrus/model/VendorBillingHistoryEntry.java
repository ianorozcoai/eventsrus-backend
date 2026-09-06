package com.backend.eventsrus.model;

import com.backend.eventsrus.common.BaseEntity;
import com.backend.eventsrus.enums.BillingSource;
import com.backend.eventsrus.enums.PlanTier;
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
 * One row per billing event for a vendor - either the free-trial grant, or
 * a PayPal payment (initial or renewal). This exists because
 * VendorSubscription itself is mutated in place on each renewal
 * (currentPeriodStart/currentPeriodEnd get overwritten), so it can't answer
 * "show me every period this vendor has had and what they paid" - this
 * table snapshots plan/period/amount at the moment of each event instead of
 * relying on the parent row's current (and constantly-changing) state.
 */
@Entity
@Table(name = "vendor_billing_history")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class VendorBillingHistoryEntry extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_subscription_id", nullable = false)
    private VendorSubscription vendorSubscription;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlanTier plan;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_source", nullable = false)
    private BillingSource billingSource;

    /** Null for a free grant. */
    private BigDecimal amount;

    /** Null for a free grant. */
    private String currency;

    /** Null for a free grant; unique when present (Postgres allows multiple NULLs in a UNIQUE column). */
    @Column(name = "paypal_transaction_id", unique = true)
    private String paypalTransactionId;

    @Column(name = "period_start", nullable = false)
    private Instant periodStart;

    @Column(name = "period_end", nullable = false)
    private Instant periodEnd;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
}
