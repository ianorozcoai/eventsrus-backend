package com.backend.eventsrus.model;

import com.backend.eventsrus.common.BaseEntity;
import com.backend.eventsrus.enums.TicketCategory;
import com.backend.eventsrus.enums.TicketStatus;
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

/**
 * A planner or vendor's support request - either about a specific
 * transaction (an event/booking/quotation gone wrong) or a general system
 * concern. Metadata only, same split as Conversation/ConversationMessage -
 * the actual complaint text and every back-and-forth reply live in
 * SupportTicketMessage, not here.
 */
@Entity
@Table(name = "support_tickets")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class SupportTicket extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "raised_by_user_id", nullable = false)
    private User raisedBy;

    @Column(nullable = false)
    private String subject;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketStatus status;

    // Optional - which transaction this is about, if any. Plain nullable ids
    // rather than @ManyToOne relations (a ticket should still load even if,
    // say, the referenced event is later deleted - not worth a hard FK here).
    @Column(name = "related_event_id")
    private Long relatedEventId;

    @Column(name = "related_booking_id")
    private Long relatedBookingId;

    @Column(name = "related_quotation_id")
    private Long relatedQuotationId;

    // Unassigned until an admin picks it up - no admin UI exists yet to set
    // this, but the column is real so one can be wired in later without a
    // schema change.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_admin_user_id")
    private User assignedAdmin;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "closed_at")
    private Instant closedAt;
}
