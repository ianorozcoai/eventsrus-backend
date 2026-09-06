package com.backend.eventsrus.repository;

import com.backend.eventsrus.model.Conversation;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    Optional<Conversation> findByEventIdAndVendorUserId(Long eventId, Long vendorUserId);

    List<Conversation> findByPlannerUserIdOrderByUpdatedAtDesc(Long plannerUserId);

    List<Conversation> findByVendorUserIdOrderByUpdatedAtDesc(Long vendorUserId);

    List<Conversation> findByEventId(Long eventId);
}
