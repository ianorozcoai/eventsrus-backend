package com.backend.eventsrus.model;

import com.backend.eventsrus.common.BaseEntity;
import com.backend.eventsrus.enums.BillingCycle;
import com.backend.eventsrus.enums.BillingSource;
import com.backend.eventsrus.enums.PlanTier;
import com.backend.eventsrus.enums.SubscriptionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "vendor_subscriptions")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class VendorSubscription extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Null until PayPal confirms the subscription is ACTIVE (unknown while APPROVAL_PENDING). */
    @Column(name = "current_period_start")
    private Instant currentPeriodStart;

    @Column(name = "current_period_end")
    private Instant currentPeriodEnd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlanTier plan;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_cycle")
    private BillingCycle billingCycle;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_source", nullable = false)
    private BillingSource billingSource;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionStatus status;

    @Column(name = "paypal_subscription_id", unique = true)
    private String paypalSubscriptionId;

    @Column(name = "paypal_plan_id")
    private String paypalPlanId;
}
