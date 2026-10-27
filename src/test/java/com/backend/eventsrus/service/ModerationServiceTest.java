package com.backend.eventsrus.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.backend.eventsrus.dto.ReportRequest;
import com.backend.eventsrus.enums.ReportContentType;
import com.backend.eventsrus.enums.ReportReason;
import com.backend.eventsrus.enums.ReportStatus;
import com.backend.eventsrus.enums.Role;
import com.backend.eventsrus.exception.ModerationRequestException;
import com.backend.eventsrus.exception.UserBlockedException;
import com.backend.eventsrus.model.ContentReport;
import com.backend.eventsrus.model.Conversation;
import com.backend.eventsrus.model.ConversationMessage;
import com.backend.eventsrus.model.User;
import com.backend.eventsrus.model.UserBlock;
import com.backend.eventsrus.repository.ContentReportRepository;
import com.backend.eventsrus.repository.ConversationMessageRepository;
import com.backend.eventsrus.repository.ConversationRepository;
import com.backend.eventsrus.repository.UserBlockRepository;
import com.backend.eventsrus.repository.UserRepository;
import com.backend.eventsrus.repository.VendorReviewRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ModerationServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private UserBlockRepository userBlockRepository;
    @Mock private ContentReportRepository contentReportRepository;
    @Mock private ConversationRepository conversationRepository;
    @Mock private ConversationMessageRepository conversationMessageRepository;
    @Mock private VendorReviewRepository vendorReviewRepository;
    @Mock private AdminNotificationEmailService adminNotificationEmailService;

    private ModerationService service;

    private static final User PLANNER =
            User.builder().id(2L).email("planner@example.com").firstName("Ana").lastName("Cruz").role(Role.PLANNER).build();
    private static final User VENDOR =
            User.builder().id(1L).email("vendor@example.com").firstName("Ben").lastName("Reyes").role(Role.VENDOR).build();
    private static final User OUTSIDER =
            User.builder().id(3L).email("other@example.com").role(Role.PLANNER).build();

    @BeforeEach
    void setUp() {
        service = new ModerationService(
                userRepository, userBlockRepository, contentReportRepository, conversationRepository,
                conversationMessageRepository, vendorReviewRepository, adminNotificationEmailService);
    }

    @Nested
    class AdminBlockList {

        @Test
        void showsWhoBlockedWhomWithNamesRolesAndDate() {
            UserBlock block = UserBlock.builder().blocker(PLANNER).blocked(VENDOR).build();
            block.setId(5L);
            when(userBlockRepository.findTop500ByOrderByCreatedAtDesc()).thenReturn(java.util.List.of(block));

            var blocks = service.listBlocksForAdmin();

            assertThat(blocks).hasSize(1);
            assertThat(blocks.get(0).blockerName()).isEqualTo("Ana Cruz");
            assertThat(blocks.get(0).blockerRole()).isEqualTo(Role.PLANNER);
            assertThat(blocks.get(0).blockedName()).isEqualTo("Ben Reyes");
            assertThat(blocks.get(0).blockedRole()).isEqualTo(Role.VENDOR);
            assertThat(blocks.get(0).blockedUserId()).isEqualTo(1L);
        }

        @Test
        void aUserWithNoNameIsShownGenerically() {
            UserBlock block = UserBlock.builder().blocker(OUTSIDER).blocked(VENDOR).build();
            when(userBlockRepository.findTop500ByOrderByCreatedAtDesc()).thenReturn(java.util.List.of(block));

            assertThat(service.listBlocksForAdmin().get(0).blockerName()).isEqualTo("EventsRUs user");
        }
    }

    @Nested
    class Block {

        @Test
        void blocksAnotherUserOnce() {
            when(userRepository.findByEmail("planner@example.com")).thenReturn(Optional.of(PLANNER));
            when(userRepository.findById(1L)).thenReturn(Optional.of(VENDOR));
            when(userBlockRepository.existsByBlockerIdAndBlockedId(2L, 1L)).thenReturn(false);

            service.block("planner@example.com", 1L);

            ArgumentCaptor<UserBlock> saved = ArgumentCaptor.forClass(UserBlock.class);
            verify(userBlockRepository).save(saved.capture());
            assertThat(saved.getValue().getBlocker()).isEqualTo(PLANNER);
            assertThat(saved.getValue().getBlocked()).isEqualTo(VENDOR);
        }

        @Test
        void blockingTwiceDoesNotDuplicate() {
            when(userRepository.findByEmail("planner@example.com")).thenReturn(Optional.of(PLANNER));
            when(userRepository.findById(1L)).thenReturn(Optional.of(VENDOR));
            when(userBlockRepository.existsByBlockerIdAndBlockedId(2L, 1L)).thenReturn(true);

            service.block("planner@example.com", 1L);

            verify(userBlockRepository, never()).save(any());
        }

        @Test
        void cannotBlockYourselfAnAdminOrADeletedUser() {
            User admin = User.builder().id(9L).role(Role.ADMIN).build();
            User deleted = User.builder().id(8L).role(Role.VENDOR).deletedAt(java.time.Instant.now()).build();
            when(userRepository.findByEmail("planner@example.com")).thenReturn(Optional.of(PLANNER));
            when(userRepository.findById(2L)).thenReturn(Optional.of(PLANNER));
            when(userRepository.findById(9L)).thenReturn(Optional.of(admin));
            when(userRepository.findById(8L)).thenReturn(Optional.of(deleted));

            assertThatThrownBy(() -> service.block("planner@example.com", 2L)).isInstanceOf(ModerationRequestException.class);
            assertThatThrownBy(() -> service.block("planner@example.com", 9L)).isInstanceOf(ModerationRequestException.class);
            assertThatThrownBy(() -> service.block("planner@example.com", 8L)).isInstanceOf(ModerationRequestException.class);
            verify(userBlockRepository, never()).save(any());
        }

        @Test
        void messagingIsRefusedWhenBlockedInEitherDirection() {
            when(userBlockRepository.existsByBlockerIdAndBlockedId(2L, 1L)).thenReturn(false);
            when(userBlockRepository.existsByBlockerIdAndBlockedId(1L, 2L)).thenReturn(true);

            assertThatThrownBy(() -> service.requireNotBlocked(PLANNER, VENDOR)).isInstanceOf(UserBlockedException.class);
        }

        @Test
        void messagingIsAllowedWhenNobodyIsBlocked() {
            when(userBlockRepository.existsByBlockerIdAndBlockedId(2L, 1L)).thenReturn(false);
            when(userBlockRepository.existsByBlockerIdAndBlockedId(1L, 2L)).thenReturn(false);

            service.requireNotBlocked(PLANNER, VENDOR);
        }
    }

    @Nested
    class Report {

        private ReportRequest request(ReportContentType type, Long contentId) {
            ReportRequest request = new ReportRequest();
            request.setContentType(type);
            request.setContentId(contentId);
            request.setReason(ReportReason.HARASSMENT);
            request.setDetails("  rude  ");
            return request;
        }

        private ConversationMessage messageFromVendor() {
            Conversation conversation = Conversation.builder().id(7L).vendorUser(VENDOR).plannerUser(PLANNER).build();
            return ConversationMessage.builder().id(55L).conversation(conversation).sender(VENDOR).body("hello").build();
        }

        @Test
        void aParticipantCanReportAMessageAndTheAdminIsTold() {
            when(userRepository.findByEmail("planner@example.com")).thenReturn(Optional.of(PLANNER));
            when(contentReportRepository.findByReporterIdAndContentTypeAndContentId(2L, ReportContentType.MESSAGE, 55L))
                    .thenReturn(Optional.empty());
            when(conversationMessageRepository.findById(55L)).thenReturn(Optional.of(messageFromVendor()));
            when(contentReportRepository.save(any())).thenAnswer(inv -> {
                ContentReport report = inv.getArgument(0);
                report.setId(100L);
                return report;
            });

            var response = service.report("planner@example.com", request(ReportContentType.MESSAGE, 55L));

            assertThat(response.id()).isEqualTo(100L);
            assertThat(response.status()).isEqualTo(ReportStatus.OPEN);
            ArgumentCaptor<ContentReport> saved = ArgumentCaptor.forClass(ContentReport.class);
            verify(contentReportRepository).save(saved.capture());
            assertThat(saved.getValue().getReportedUser()).isEqualTo(VENDOR);
            assertThat(saved.getValue().getDetails()).isEqualTo("rude");
            verify(adminNotificationEmailService).notifyNewReport(
                    "MESSAGE", 55L, "HARASSMENT", "rude", "Ben Reyes", "Ana Cruz");
        }

        @Test
        void aMessageFromSomeoneElsesConversationCannotBeReported() {
            when(userRepository.findByEmail("other@example.com")).thenReturn(Optional.of(OUTSIDER));
            when(contentReportRepository.findByReporterIdAndContentTypeAndContentId(3L, ReportContentType.MESSAGE, 55L))
                    .thenReturn(Optional.empty());
            when(conversationMessageRepository.findById(55L)).thenReturn(Optional.of(messageFromVendor()));

            assertThatThrownBy(() -> service.report("other@example.com", request(ReportContentType.MESSAGE, 55L)))
                    .isInstanceOf(ModerationRequestException.class);
            verify(contentReportRepository, never()).save(any());
        }

        @Test
        void reportingTheSameItemTwiceCreatesNoSecondReport() {
            ContentReport existing = ContentReport.builder().id(100L).status(ReportStatus.OPEN).build();
            when(userRepository.findByEmail("planner@example.com")).thenReturn(Optional.of(PLANNER));
            when(contentReportRepository.findByReporterIdAndContentTypeAndContentId(2L, ReportContentType.MESSAGE, 55L))
                    .thenReturn(Optional.of(existing));

            var response = service.report("planner@example.com", request(ReportContentType.MESSAGE, 55L));

            assertThat(response.id()).isEqualTo(100L);
            verify(contentReportRepository, never()).save(any());
            verify(adminNotificationEmailService, never()).notifyNewReport(any(), any(), any(), any(), any(), any());
        }

        @Test
        void aStorefrontCanBeReportedButNotYourself() {
            when(userRepository.findByEmail("planner@example.com")).thenReturn(Optional.of(PLANNER));
            when(userRepository.findById(1L)).thenReturn(Optional.of(VENDOR));
            when(userRepository.findById(2L)).thenReturn(Optional.of(PLANNER));
            when(contentReportRepository.findByReporterIdAndContentTypeAndContentId(any(), any(), any()))
                    .thenReturn(Optional.empty());
            when(contentReportRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.report("planner@example.com", request(ReportContentType.STOREFRONT, 1L));
            verify(contentReportRepository).save(any());

            assertThatThrownBy(() -> service.report("planner@example.com", request(ReportContentType.STOREFRONT, 2L)))
                    .isInstanceOf(ModerationRequestException.class);
        }

        @Test
        void aPersonCanOnlyBeReportedIfYouShareAConversation() {
            when(userRepository.findByEmail("other@example.com")).thenReturn(Optional.of(OUTSIDER));
            when(userRepository.findById(1L)).thenReturn(Optional.of(VENDOR));
            when(contentReportRepository.findByReporterIdAndContentTypeAndContentId(3L, ReportContentType.USER, 1L))
                    .thenReturn(Optional.empty());
            when(conversationRepository.existsByPlannerUserIdAndVendorUserId(3L, 1L)).thenReturn(false);
            when(conversationRepository.existsByPlannerUserIdAndVendorUserId(1L, 3L)).thenReturn(false);

            assertThatThrownBy(() -> service.report("other@example.com", request(ReportContentType.USER, 1L)))
                    .isInstanceOf(ModerationRequestException.class)
                    .hasMessageContaining("conversation");
        }
    }

    @Test
    void adminResolvingMarksTheReportResolvedWithANote() {
        ContentReport report = ContentReport.builder().id(100L).reporter(PLANNER).reportedUser(VENDOR)
                .contentType(ReportContentType.USER).contentId(1L).reason(ReportReason.SPAM)
                .status(ReportStatus.OPEN).build();
        when(contentReportRepository.findById(100L)).thenReturn(Optional.of(report));
        when(contentReportRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = service.resolve(100L, "  Warned the supplier  ");

        assertThat(response.status()).isEqualTo(ReportStatus.RESOLVED);
        assertThat(response.resolvedNote()).isEqualTo("Warned the supplier");
        assertThat(response.resolvedAt()).isNotNull();
    }
}
