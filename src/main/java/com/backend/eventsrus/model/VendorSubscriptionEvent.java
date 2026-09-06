package com.backend.eventsrus.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "vendor_subscription_events")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class VendorSubscriptionEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Null when the webhook couldn't be mapped to a known subscription (still logged for audit/debugging). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_subscription_id")
    private VendorSubscription vendorSubscription;

    @Column(name = "paypal_event_id", nullable = false, unique = true)
    private String paypalEventId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(columnDefinition = "TEXT")
    private String payload;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
