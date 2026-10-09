package com.backend.eventsrus.model;

import com.backend.eventsrus.enums.SmsTriggerType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Audit trail for every SMS send attempt via Semaphore - both admin ad-hoc
 * sends and the vendor quotation/booking triggers. Rows are only ever
 * written for an actual send attempt (see SmsService) - nothing is logged
 * while app.sms-enabled=false, to avoid filling this table with noise before
 * the Semaphore sender name is approved.
 */
@Entity
@Table(name = "sms_logs")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class SmsLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recipient_number", nullable = false)
    private String recipientNumber;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false)
    private SmsTriggerType triggerType;

    @Column(nullable = false)
    private boolean success;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "sent_by_admin_email")
    private String sentByAdminEmail;

    @Column(name = "related_entity_type")
    private String relatedEntityType;

    @Column(name = "related_entity_id")
    private Long relatedEntityId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
