package com.backend.eventsrus.service;

import com.backend.eventsrus.dto.ConversationMessageResponse;
import com.backend.eventsrus.dto.ConversationSummaryResponse;
import com.backend.eventsrus.enums.NotificationType;
import com.backend.eventsrus.enums.Role;
import com.backend.eventsrus.model.Conversation;
import com.backend.eventsrus.model.ConversationMessage;
import com.backend.eventsrus.model.Event;
import com.backend.eventsrus.model.User;
import com.backend.eventsrus.model.VendorProfile;
import com.backend.eventsrus.repository.ConversationMessageRepository;
import com.backend.eventsrus.repository.ConversationRepository;
import com.backend.eventsrus.repository.EventRepository;
import com.backend.eventsrus.repository.UserRepository;
import com.backend.eventsrus.repository.VendorProfileRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final ConversationMessageRepository conversationMessageRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final VendorProfileRepository vendorProfileRepository;
    private final NotificationService notificationService;
    private final VendorPlanService vendorPlanService;

    /**
     * Planner's initial inquiry — finds or creates the (event, vendor)
     * conversation and posts the opening message. plannerName is the
     * storefront form's free-text "Your Full Name" field (distinct from the
     * account's own name - the planner filling this in may be booking on
     * someone else's behalf) - composed into the message body along with
     * the target date so the vendor sees a complete, self-contained
     * inquiry rather than just the raw question with that context lost.
     */
    @Transactional
    public ConversationMessageResponse sendInquiry(
            String plannerEmail, Long eventId, Long vendorUserId, String plannerName, LocalDate targetDate,
            String message) {
        User planner = requireUser(plannerEmail);
        User vendor = userRepository.findById(vendorUserId)
                .orElseThrow(() -> new IllegalStateException("Vendor not found: " + vendorUserId));
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalStateException("Event not found: " + eventId));

        Conversation conversation = conversationRepository.findByEventIdAndVendorUserId(eventId, vendorUserId)
                .orElseGet(() -> conversationRepository.save(Conversation.builder()
                        .event(event)
                        .vendorUser(vendor)
                        .plannerUser(planner)
                        .build()));

        return postMessage(conversation, planner, vendor, targetDate, composeInquiryBody(plannerName, targetDate, message));
    }

    private String composeInquiryBody(String plannerName, LocalDate targetDate, String message) {
        StringBuilder sb = new StringBuilder();
        sb.append("Name: ").append(plannerName).append("\n");
        if (targetDate != null) {
            sb.append("Event Date: ").append(targetDate.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))).append("\n");
        }
        sb.append("\nInquiry Message:\n\n").append(message);
        return sb.toString();
    }

    /**
     * A vendor reaching out first to a lead (a planner who visited their
     * storefront but hasn't messaged yet) - finds or creates the
     * (event, vendor) conversation and posts the opening message, same
     * shape as sendInquiry above but initiated from the vendor's side
     * instead of the planner's.
     */
    @Transactional
    public ConversationMessageResponse sendVendorMessage(String vendorEmail, Long eventId, String message) {
        User vendor = requireUser(vendorEmail);
        vendorPlanService.requireActiveSubscription(vendor.getId());
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalStateException("Event not found: " + eventId));

        Conversation conversation = conversationRepository.findByEventIdAndVendorUserId(eventId, vendor.getId())
                .orElseGet(() -> conversationRepository.save(Conversation.builder()
                        .event(event)
                        .vendorUser(vendor)
                        .plannerUser(event.getPlanner())
                        .build()));

        return postMessage(conversation, vendor, event.getPlanner(), null, message);
    }

    @Transactional
    public ConversationMessageResponse sendMessage(String senderEmail, Long conversationId, String message) {
        User sender = requireUser(senderEmail);
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalStateException("Conversation not found: " + conversationId));

        boolean senderIsVendor = conversation.getVendorUser().getId().equals(sender.getId());
        User recipient = senderIsVendor ? conversation.getPlannerUser() : conversation.getVendorUser();
        requireParticipant(conversation, sender);
        if (senderIsVendor) {
            vendorPlanService.requireActiveSubscription(sender.getId());
        }

        return postMessage(conversation, sender, recipient, null, message);
    }

    @Transactional
    public List<ConversationMessageResponse> getMessages(String requesterEmail, Long conversationId) {
        User requester = requireUser(requesterEmail);
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalStateException("Conversation not found: " + conversationId));
        requireParticipant(conversation, requester);

        List<ConversationMessage> messages =
                conversationMessageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);

        messages.stream()
                .filter(m -> m.getReadAt() == null && !m.getSender().getId().equals(requester.getId()))
                .forEach(m -> {
                    m.setReadAt(Instant.now());
                    conversationMessageRepository.save(m);
                });

        return messages.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<ConversationSummaryResponse> listConversations(String email) {
        User user = requireUser(email);
        List<Conversation> asPlanner = conversationRepository.findByPlannerUserIdOrderByUpdatedAtDesc(user.getId());
        List<Conversation> asVendor = conversationRepository.findByVendorUserIdOrderByUpdatedAtDesc(user.getId());

        List<Conversation> all = new java.util.ArrayList<>();
        all.addAll(asPlanner);
        all.addAll(asVendor);

        return all.stream().map(c -> toSummary(c, user)).toList();
    }

    private ConversationMessageResponse postMessage(
            Conversation conversation, User sender, User recipient, LocalDate targetDate, String message) {
        ConversationMessage saved = conversationMessageRepository.save(ConversationMessage.builder()
                .conversation(conversation)
                .sender(sender)
                .targetDate(targetDate)
                .body(message)
                .build());

        notificationService.notify(recipient, NotificationType.NEW_MESSAGE,
                "New message from " + displayName(sender),
                message.length() > 140 ? message.substring(0, 140) + "..." : message,
                "CONVERSATION", conversation.getId());

        return toResponse(saved);
    }

    private void requireParticipant(Conversation conversation, User user) {
        boolean participant = conversation.getPlannerUser().getId().equals(user.getId())
                || conversation.getVendorUser().getId().equals(user.getId());
        if (!participant) {
            throw new IllegalStateException("User is not a participant in this conversation");
        }
    }

    private User requireUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + email));
    }

    private ConversationMessageResponse toResponse(ConversationMessage message) {
        return ConversationMessageResponse.builder()
                .id(message.getId())
                .senderUserId(message.getSender().getId())
                .senderName(displayName(message.getSender()))
                .targetDate(message.getTargetDate())
                .body(message.getBody())
                .createdAt(message.getCreatedAt())
                .build();
    }

    private ConversationSummaryResponse toSummary(Conversation conversation, User viewer) {
        List<ConversationMessage> messages =
                conversationMessageRepository.findByConversationIdOrderByCreatedAtAsc(conversation.getId());
        ConversationMessage last = messages.isEmpty() ? null : messages.get(messages.size() - 1);
        long unread = conversationMessageRepository
                .countByConversationIdAndReadAtIsNullAndSenderIdNot(conversation.getId(), viewer.getId());

        boolean viewerIsVendor = conversation.getVendorUser().getId().equals(viewer.getId());
        User otherParty = viewerIsVendor ? conversation.getPlannerUser() : conversation.getVendorUser();

        // Only populated when the viewer is a planner looking at a vendor -
        // lets the Chats header show the vendor's category and a "View
        // Storefront" link, same info a planner would find useful there.
        Optional<VendorProfile> otherPartyProfile = viewerIsVendor
                ? Optional.empty()
                : vendorProfileRepository.findByUserId(otherParty.getId());

        return ConversationSummaryResponse.builder()
                .id(conversation.getId())
                .eventId(conversation.getEvent().getId())
                .eventName(conversation.getEvent().getName())
                .eventDate(conversation.getEvent().getEventDate())
                .otherPartyUserId(otherParty.getId())
                .otherPartyName(displayName(otherParty))
                .otherPartyBusinessType(otherPartyProfile
                        .flatMap(p -> p.getBusinessTypes().stream().findFirst())
                        .orElse(null))
                .otherPartySlug(otherPartyProfile.map(VendorProfile::getSlug).orElse(null))
                .lastMessagePreview(last != null ? last.getBody() : null)
                .lastMessageAt(last != null ? last.getCreatedAt() : null)
                .unreadCount(unread)
                .build();
    }

    /**
     * A vendor should always be identified by their business name, not the
     * personal name of whoever's logged into that account - a planner
     * talking to "Blossom & Bloom Florals" shouldn't see "Ian Orozco" in
     * the chat header/notifications instead. Planners have no business
     * name concept, so they still fall back to personal name/email.
     */
    private String displayName(User user) {
        if (user.getRole() == Role.VENDOR) {
            String businessName = vendorProfileRepository.findByUserId(user.getId())
                    .map(VendorProfile::getBusinessName)
                    .orElse(null);
            if (businessName != null) {
                return businessName;
            }
        }
        if (user.getFirstName() != null) {
            return user.getLastName() != null ? user.getFirstName() + " " + user.getLastName() : user.getFirstName();
        }
        return user.getEmail();
    }
}
