package com.backend.eventsrus.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.backend.eventsrus.enums.Role;
import com.backend.eventsrus.exception.InvalidFileTypeException;
import com.backend.eventsrus.model.Conversation;
import com.backend.eventsrus.model.Event;
import com.backend.eventsrus.model.User;
import com.backend.eventsrus.repository.ConversationMessageRepository;
import com.backend.eventsrus.repository.ConversationRepository;
import com.backend.eventsrus.repository.EventRepository;
import com.backend.eventsrus.repository.UserRepository;
import com.backend.eventsrus.repository.VendorProfileRepository;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

/**
 * Image attachments on chat messages - mirrors SupportTicketService's own
 * attachment handling (same validation, same private-bucket + presigned-URL
 * pattern), extended onto the planner<->vendor conversation thread.
 */
@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private ConversationMessageRepository conversationMessageRepository;
    @Mock
    private EventRepository eventRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private VendorProfileRepository vendorProfileRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private VendorPlanService vendorPlanService;
    @Mock
    private S3UploadService s3UploadService;

    private ConversationService conversationService;

    private static final User VENDOR = User.builder().id(1L).email("vendor@example.com").role(Role.VENDOR).build();
    private static final User PLANNER = User.builder().id(2L).email("planner@example.com").role(Role.PLANNER).build();

    @BeforeEach
    void setUp() {
        conversationService = new ConversationService(
                conversationRepository, conversationMessageRepository, eventRepository, userRepository,
                vendorProfileRepository, notificationService, vendorPlanService, s3UploadService);
    }

    private Conversation conversation() {
        Event event = Event.builder().id(9L).name("Ian's wedding").build();
        return Conversation.builder().id(7L).event(event).vendorUser(VENDOR).plannerUser(PLANNER).build();
    }

    @Test
    void sendMessageWithValidImageAttachmentStoresKeyAndReturnsPresignedUrl() {
        Conversation conversation = conversation();
        when(userRepository.findByEmail("planner@example.com")).thenReturn(Optional.of(PLANNER));
        when(conversationRepository.findById(7L)).thenReturn(Optional.of(conversation));
        when(conversationMessageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(s3UploadService.upload(any(), anyString(), eq(S3UploadService.Visibility.PRIVATE)))
                .thenReturn(new S3UploadService.UploadResult("conversations/7/attachment-abc.jpg", null));
        when(s3UploadService.presignedUrl(eq("conversations/7/attachment-abc.jpg"), any(Duration.class)))
                .thenReturn("https://example.com/presigned/attachment-abc.jpg");

        MockMultipartFile attachment =
                new MockMultipartFile("attachment", "cake.jpg", "image/jpeg", "content".getBytes());

        var response = conversationService.sendMessage(
                "planner@example.com", 7L, "Here's what I'm picturing", attachment);

        assertThat(response.getAttachmentUrl()).isEqualTo("https://example.com/presigned/attachment-abc.jpg");
    }

    @Test
    void sendMessageWithInvalidAttachmentTypeThrowsInvalidFileTypeException() {
        Conversation conversation = conversation();
        when(userRepository.findByEmail("planner@example.com")).thenReturn(Optional.of(PLANNER));
        when(conversationRepository.findById(7L)).thenReturn(Optional.of(conversation));

        MockMultipartFile attachment =
                new MockMultipartFile("attachment", "notes.pdf", "application/pdf", "content".getBytes());

        assertThatThrownBy(() -> conversationService.sendMessage(
                "planner@example.com", 7L, "Here's what I'm picturing", attachment))
                .isInstanceOf(InvalidFileTypeException.class);

        verify(conversationMessageRepository, never()).save(any());
    }

    @Test
    void sendMessageWithNoAttachmentLeavesAttachmentUrlNull() {
        Conversation conversation = conversation();
        when(userRepository.findByEmail("planner@example.com")).thenReturn(Optional.of(PLANNER));
        when(conversationRepository.findById(7L)).thenReturn(Optional.of(conversation));
        when(conversationMessageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = conversationService.sendMessage("planner@example.com", 7L, "Just checking in", null);

        assertThat(response.getAttachmentUrl()).isNull();
    }
}
