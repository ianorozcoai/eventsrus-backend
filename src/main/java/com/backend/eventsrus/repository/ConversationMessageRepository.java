package com.backend.eventsrus.repository;

import com.backend.eventsrus.model.ConversationMessage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationMessageRepository extends JpaRepository<ConversationMessage, Long> {

    List<ConversationMessage> findByConversationIdOrderByCreatedAtAsc(Long conversationId);

    long countByConversationIdAndReadAtIsNullAndSenderIdNot(Long conversationId, Long excludeSenderId);
}
