package com.backend.eventsrus.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.CreationTimestamp;

/** A renewal-reminder SMS that has already been sent (see SubscriptionReminderService). */
@Entity
@Table(name = "subscription_reminders")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class SubscriptionReminder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "access_end_date", nullable = false)
    private LocalDate accessEndDate;

    @Column(name = "days_before", nullable = false)
    private int leadDays;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
