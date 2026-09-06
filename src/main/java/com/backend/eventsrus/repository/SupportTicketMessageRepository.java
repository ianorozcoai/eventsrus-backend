package com.backend.eventsrus.repository;

import com.backend.eventsrus.model.SupportTicketMessage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupportTicketMessageRepository extends JpaRepository<SupportTicketMessage, Long> {

    List<SupportTicketMessage> findByTicketIdOrderByCreatedAtAsc(Long ticketId);
}
