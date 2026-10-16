package com.backend.eventsrus.service;

import com.backend.eventsrus.dto.AdminReportResponse;
import com.backend.eventsrus.dto.BlockedUserResponse;
import com.backend.eventsrus.dto.ReportRequest;
import com.backend.eventsrus.dto.ReportResponse;
import com.backend.eventsrus.enums.ReportContentType;
import com.backend.eventsrus.enums.ReportStatus;
import com.backend.eventsrus.enums.Role;
import com.backend.eventsrus.exception.ModerationRequestException;
import com.backend.eventsrus.exception.UserBlockedException;
import com.backend.eventsrus.model.ContentReport;
import com.backend.eventsrus.model.Conversation;
import com.backend.eventsrus.model.ConversationMessage;
import com.backend.eventsrus.model.User;
import com.backend.eventsrus.model.UserBlock;
import com.backend.eventsrus.model.VendorReview;
import com.backend.eventsrus.repository.ContentReportRepository;
import com.backend.eventsrus.repository.ConversationMessageRepository;
import com.backend.eventsrus.repository.ConversationRepository;
import com.backend.eventsrus.repository.UserBlockRepository;
import com.backend.eventsrus.repository.UserRepository;
import com.backend.eventsrus.repository.VendorReviewRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Report and block (Google Play User Generated Content policy). Deliberately light:
 * <ul>
 *   <li>A <b>report</b> is only a note for the admin. It never hides, removes or blocks anything by
 *       itself; the admin decides. One report per person per item; the reported person is never told
 *       who reported; a message can only be reported by someone in that conversation.</li>
 *   <li>A <b>block</b> only stops the two people messaging each other (either direction). Old
 *       messages stay visible, nothing is deleted, the blocked person is not told.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ModerationService {

    private static final int PREVIEW_LENGTH = 200;

    private final UserRepository userRepository;
    private final UserBlockRepository userBlockRepository;
    private final ContentReportRepository contentReportRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationMessageRepository conversationMessageRepository;
    private final VendorReviewRepository vendorReviewRepository;
    private final AdminNotificationEmailService adminNotificationEmailService;

    // ---------------------------------------------------------------- block

    @Transactional
    public void block(String email, Long targetUserId) {
        User blocker = requireUser(email);
        User target = requireTarget(blocker, targetUserId);
        if (!userBlockRepository.existsByBlockerIdAndBlockedId(blocker.getId(), target.getId())) {
            userBlockRepository.save(UserBlock.builder().blocker(blocker).blocked(target).build());
        }
    }

    @Transactional
    public void unblock(String email, Long targetUserId) {
        User blocker = requireUser(email);
        userBlockRepository.findByBlockerIdAndBlockedId(blocker.getId(), targetUserId)
                .ifPresent(userBlockRepository::delete);
    }

    @Transactional(readOnly = true)
    public List<BlockedUserResponse> listBlocked(String email) {
        User blocker = requireUser(email);
        return userBlockRepository.findByBlockerIdOrderByCreatedAtDesc(blocker.getId()).stream()
                .map(block -> new BlockedUserResponse(
                        block.getBlocked().getId(), displayName(block.getBlocked()), block.getCreatedAt()))
                .toList();
    }

    /** True if either user has blocked the other. */
    @Transactional(readOnly = true)
    public boolean isBlockedEitherWay(Long userA, Long userB) {
        return userBlockRepository.existsByBlockerIdAndBlockedId(userA, userB)
                || userBlockRepository.existsByBlockerIdAndBlockedId(userB, userA);
    }

    /** Called before any message is posted. The wording does not reveal which side blocked. */
    public void requireNotBlocked(User sender, User recipient) {
        if (isBlockedEitherWay(sender.getId(), recipient.getId())) {
            throw new UserBlockedException("You can't send messages to this user.");
        }
    }

    // --------------------------------------------------------------- report

    @Transactional
    public ReportResponse report(String email, ReportRequest request) {
        User reporter = requireUser(email);

        var existing = contentReportRepository.findByReporterIdAndContentTypeAndContentId(
                reporter.getId(), request.getContentType(), request.getContentId());
        if (existing.isPresent()) {
            // Already reported: answer the same way, no duplicate for the admin.
            return new ReportResponse(existing.get().getId(), existing.get().getStatus());
        }

        User reported = resolveReportedUser(reporter, request.getContentType(), request.getContentId());
        if (reported.getId().equals(reporter.getId())) {
            throw new ModerationRequestException("You can't report yourself.");
        }

        ContentReport saved = contentReportRepository.save(ContentReport.builder()
                .reporter(reporter)
                .reportedUser(reported)
                .contentType(request.getContentType())
                .contentId(request.getContentId())
                .reason(request.getReason())
                .details(trimToNull(request.getDetails()))
                .status(ReportStatus.OPEN)
                .build());
        adminNotificationEmailService.notifyNewReport(
                saved.getContentType().name(), saved.getContentId(), saved.getReason().name(), saved.getDetails(),
                displayName(reported), displayName(reporter));
        return new ReportResponse(saved.getId(), saved.getStatus());
    }

    private User resolveReportedUser(User reporter, ReportContentType type, Long contentId) {
        return switch (type) {
            case MESSAGE -> {
                ConversationMessage message = conversationMessageRepository.findById(contentId)
                        .orElseThrow(() -> new ModerationRequestException("That message no longer exists."));
                Conversation conversation = message.getConversation();
                boolean participant = conversation.getPlannerUser().getId().equals(reporter.getId())
                        || conversation.getVendorUser().getId().equals(reporter.getId());
                if (!participant) {
                    throw new ModerationRequestException("That message no longer exists.");
                }
                yield message.getSender();
            }
            case REVIEW -> {
                VendorReview review = vendorReviewRepository.findById(contentId)
                        .orElseThrow(() -> new ModerationRequestException("That review no longer exists."));
                yield review.getBooking().getPlannerUser();
            }
            case STOREFRONT -> requireTarget(reporter, contentId);
            case USER -> {
                User target = requireTarget(reporter, contentId);
                if (!sharesConversation(reporter, target)) {
                    throw new ModerationRequestException(
                            "You can only report someone you have a conversation with. "
                                    + "Report one of their messages or their storefront instead.");
                }
                yield target;
            }
        };
    }

    private boolean sharesConversation(User a, User b) {
        return conversationRepository.existsByPlannerUserIdAndVendorUserId(a.getId(), b.getId())
                || conversationRepository.existsByPlannerUserIdAndVendorUserId(b.getId(), a.getId());
    }

    // ---------------------------------------------------------------- admin

    @Transactional(readOnly = true)
    public List<AdminReportResponse> listForAdmin(ReportStatus status) {
        List<ContentReport> reports = status == null
                ? contentReportRepository.findAllByOrderByCreatedAtDesc()
                : contentReportRepository.findByStatusOrderByCreatedAtDesc(status);
        return reports.stream().map(this::toAdminResponse).toList();
    }

    @Transactional
    public AdminReportResponse resolve(Long reportId, String note) {
        ContentReport report = contentReportRepository.findById(reportId)
                .orElseThrow(() -> new ModerationRequestException("Report not found."));
        report.setStatus(ReportStatus.RESOLVED);
        report.setResolvedAt(Instant.now());
        report.setResolvedNote(trimToNull(note));
        return toAdminResponse(contentReportRepository.save(report));
    }

    private AdminReportResponse toAdminResponse(ContentReport report) {
        return new AdminReportResponse(
                report.getId(),
                report.getContentType(),
                report.getContentId(),
                report.getReason(),
                report.getDetails(),
                report.getStatus(),
                report.getReporter().getId(),
                displayName(report.getReporter()),
                report.getReportedUser().getId(),
                displayName(report.getReportedUser()),
                preview(report),
                report.getCreatedAt(),
                report.getResolvedAt(),
                report.getResolvedNote());
    }

    /** For a reported message or review, the text itself, so the admin can judge it without opening the chat. */
    private String preview(ContentReport report) {
        if (report.getContentType() == ReportContentType.MESSAGE) {
            return conversationMessageRepository.findById(report.getContentId())
                    .map(message -> shorten(message.getBody()))
                    .orElse(null);
        }
        if (report.getContentType() == ReportContentType.REVIEW) {
            return vendorReviewRepository.findById(report.getContentId())
                    .map(review -> shorten(review.getComment()))
                    .orElse(null);
        }
        return null;
    }

    // -------------------------------------------------------------- helpers

    private User requireUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + email));
    }

    private User requireTarget(User actor, Long targetUserId) {
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ModerationRequestException("That user no longer exists."));
        if (target.getDeletedAt() != null) {
            throw new ModerationRequestException("That user no longer exists.");
        }
        if (target.getId().equals(actor.getId())) {
            throw new ModerationRequestException("You can't do that to yourself.");
        }
        if (target.getRole() == Role.ADMIN) {
            throw new ModerationRequestException("That user can't be blocked or reported here.");
        }
        return target;
    }

    private static String displayName(User user) {
        if (user.getFirstName() == null && user.getLastName() == null) {
            return "EventsRUs user";
        }
        String first = user.getFirstName() != null ? user.getFirstName() : "";
        String last = user.getLastName() != null ? user.getLastName() : "";
        return (first + " " + last).trim();
    }

    private static String trimToNull(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String shorten(String text) {
        if (text == null) {
            return null;
        }
        return text.length() <= PREVIEW_LENGTH ? text : text.substring(0, PREVIEW_LENGTH) + "...";
    }
}
