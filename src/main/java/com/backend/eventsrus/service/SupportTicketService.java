package com.backend.eventsrus.service;

import com.backend.eventsrus.dto.SupportTicketMessageResponse;
import com.backend.eventsrus.dto.SupportTicketResponse;
import com.backend.eventsrus.enums.NotificationType;
import com.backend.eventsrus.enums.Role;
import com.backend.eventsrus.enums.TicketCategory;
import com.backend.eventsrus.enums.TicketStatus;
import com.backend.eventsrus.model.Event;
import com.backend.eventsrus.model.SupportTicket;
import com.backend.eventsrus.model.SupportTicketMessage;
import com.backend.eventsrus.model.User;
import com.backend.eventsrus.repository.EventRepository;
import com.backend.eventsrus.repository.SupportTicketMessageRepository;
import com.backend.eventsrus.repository.SupportTicketRepository;
import com.backend.eventsrus.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A planner or vendor's support ticket - either about a specific transaction
 * (an event/booking/quotation) or a general system concern. Deliberately
 * symmetric between the two roles: any authenticated user can raise one,
 * reply to their own, and see only their own - there's no admin-facing UI
 * yet (Role.ADMIN exists but nothing built on it so far), so #updateStatus
 * below is real and role-checked but currently only reachable by whatever
 * calls the API directly.
 */
@Service
@RequiredArgsConstructor
public class SupportTicketService {

    private final SupportTicketRepository supportTicketRepository;
    private final SupportTicketMessageRepository supportTicketMessageRepository;
    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final NotificationService notificationService;

    @Transactional
    public SupportTicketResponse createTicket(
            String raiserEmail, String subject, TicketCategory category, String message,
            Long relatedEventId, Long relatedBookingId, Long relatedQuotationId) {
        User raiser = requireUser(raiserEmail);

        SupportTicket ticket = supportTicketRepository.save(SupportTicket.builder()
                .raisedBy(raiser)
                .subject(subject)
                .category(category)
                .status(TicketStatus.OPEN)
                .relatedEventId(relatedEventId)
                .relatedBookingId(relatedBookingId)
                .relatedQuotationId(relatedQuotationId)
                .build());

        postMessage(ticket, raiser, message);

        // No admin UI exists yet to pick this up, but every admin account
        // that does exist should still hear about it.
        userRepository.findByRole(Role.ADMIN).forEach(admin ->
                notificationService.notify(admin, NotificationType.NEW_SUPPORT_TICKET,
                        "New support ticket",
                        displayName(raiser) + " raised a ticket: " + subject,
                        "SUPPORT_TICKET", ticket.getId()));

        return toResponse(ticket);
    }

    /**
     * The ticket's raiser or an admin can reply. A reply from the raiser on
     * an already-RESOLVED ticket reopens it - that's a signal the issue
     * wasn't actually fixed, not a fresh unrelated question.
     */
    @Transactional
    public SupportTicketResponse reply(String requesterEmail, Long ticketId, String message) {
        User requester = requireUser(requesterEmail);
        SupportTicket ticket = supportTicketRepository.findById(ticketId)
                .orElseThrow(() -> new IllegalStateException("Ticket not found: " + ticketId));
        boolean isRaiser = ticket.getRaisedBy().getId().equals(requester.getId());
        if (!isRaiser && requester.getRole() != Role.ADMIN) {
            throw new IllegalStateException("Ticket does not belong to the authenticated user");
        }
        if (ticket.getStatus() == TicketStatus.CLOSED) {
            throw new IllegalStateException("This ticket is closed - raise a new one instead: " + ticketId);
        }

        postMessage(ticket, requester, message);

        if (isRaiser) {
            if (ticket.getStatus() == TicketStatus.RESOLVED) {
                ticket.setStatus(TicketStatus.OPEN);
                ticket.setResolvedAt(null);
                supportTicketRepository.save(ticket);
            }
            if (ticket.getAssignedAdmin() != null) {
                notificationService.notify(ticket.getAssignedAdmin(), NotificationType.SUPPORT_TICKET_REPLY,
                        "Ticket reply", displayName(requester) + " replied to ticket #" + ticket.getId(),
                        "SUPPORT_TICKET", ticket.getId());
            }
        } else {
            notificationService.notify(ticket.getRaisedBy(), NotificationType.SUPPORT_TICKET_REPLY,
                    "Support replied to your ticket",
                    displayName(requester) + " replied to your ticket: " + ticket.getSubject(),
                    "SUPPORT_TICKET", ticket.getId());
        }

        return toResponse(ticket);
    }

    @Transactional
    public SupportTicketResponse updateStatus(String adminEmail, Long ticketId, TicketStatus status) {
        User admin = requireUser(adminEmail);
        if (admin.getRole() != Role.ADMIN) {
            throw new IllegalStateException("Only an admin can update a ticket's status");
        }
        SupportTicket ticket = supportTicketRepository.findById(ticketId)
                .orElseThrow(() -> new IllegalStateException("Ticket not found: " + ticketId));

        ticket.setStatus(status);
        ticket.setAssignedAdmin(admin);
        if (status == TicketStatus.RESOLVED) {
            ticket.setResolvedAt(Instant.now());
        } else if (status == TicketStatus.CLOSED) {
            ticket.setClosedAt(Instant.now());
        }
        supportTicketRepository.save(ticket);

        notificationService.notify(ticket.getRaisedBy(), NotificationType.SUPPORT_TICKET_STATUS_CHANGED,
                "Ticket updated", "Your ticket \"" + ticket.getSubject() + "\" is now " + status,
                "SUPPORT_TICKET", ticket.getId());

        return toResponse(ticket);
    }

    @Transactional(readOnly = true)
    public List<SupportTicketResponse> listForUser(String email) {
        User user = requireUser(email);
        return supportTicketRepository.findByRaisedByIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public SupportTicketResponse getTicket(String requesterEmail, Long ticketId) {
        return toResponse(requireVisibleTicket(requesterEmail, ticketId));
    }

    @Transactional(readOnly = true)
    public List<SupportTicketMessageResponse> getMessages(String requesterEmail, Long ticketId) {
        SupportTicket ticket = requireVisibleTicket(requesterEmail, ticketId);
        return supportTicketMessageRepository.findByTicketIdOrderByCreatedAtAsc(ticket.getId()).stream()
                .map(this::toMessageResponse)
                .toList();
    }

    private SupportTicket requireVisibleTicket(String requesterEmail, Long ticketId) {
        User requester = requireUser(requesterEmail);
        SupportTicket ticket = supportTicketRepository.findById(ticketId)
                .orElseThrow(() -> new IllegalStateException("Ticket not found: " + ticketId));
        boolean isRaiser = ticket.getRaisedBy().getId().equals(requester.getId());
        if (!isRaiser && requester.getRole() != Role.ADMIN) {
            throw new IllegalStateException("Ticket does not belong to the authenticated user");
        }
        return ticket;
    }

    private SupportTicketMessage postMessage(SupportTicket ticket, User sender, String body) {
        return supportTicketMessageRepository.save(SupportTicketMessage.builder()
                .ticket(ticket)
                .sender(sender)
                .body(body)
                .build());
    }

    private User requireUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + email));
    }

    private String displayName(User user) {
        return user.getFirstName() != null ? user.getFirstName() : user.getEmail();
    }

    private SupportTicketResponse toResponse(SupportTicket ticket) {
        List<SupportTicketMessage> messages =
                supportTicketMessageRepository.findByTicketIdOrderByCreatedAtAsc(ticket.getId());
        SupportTicketMessage last = messages.isEmpty() ? null : messages.get(messages.size() - 1);
        Event relatedEvent = ticket.getRelatedEventId() == null
                ? null
                : eventRepository.findById(ticket.getRelatedEventId()).orElse(null);

        return SupportTicketResponse.builder()
                .id(ticket.getId())
                .subject(ticket.getSubject())
                .category(ticket.getCategory())
                .status(ticket.getStatus())
                .raisedByUserId(ticket.getRaisedBy().getId())
                .raisedByName(displayName(ticket.getRaisedBy()))
                .relatedEventId(ticket.getRelatedEventId())
                .relatedEventName(relatedEvent != null ? relatedEvent.getName() : null)
                .relatedBookingId(ticket.getRelatedBookingId())
                .relatedQuotationId(ticket.getRelatedQuotationId())
                .assignedAdminUserId(ticket.getAssignedAdmin() != null ? ticket.getAssignedAdmin().getId() : null)
                .assignedAdminName(ticket.getAssignedAdmin() != null ? displayName(ticket.getAssignedAdmin()) : null)
                .lastMessagePreview(last != null ? last.getBody() : null)
                .lastMessageAt(last != null ? last.getCreatedAt() : null)
                .resolvedAt(ticket.getResolvedAt())
                .closedAt(ticket.getClosedAt())
                .createdAt(ticket.getCreatedAt())
                .build();
    }

    private SupportTicketMessageResponse toMessageResponse(SupportTicketMessage message) {
        return SupportTicketMessageResponse.builder()
                .id(message.getId())
                .senderUserId(message.getSender().getId())
                .senderName(displayName(message.getSender()))
                .senderRole(message.getSender().getRole())
                .body(message.getBody())
                .createdAt(message.getCreatedAt())
                .build();
    }
}
