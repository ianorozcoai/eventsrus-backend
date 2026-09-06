package com.backend.eventsrus.repository;

import com.backend.eventsrus.model.SupportTicket;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {

    List<SupportTicket> findByRaisedByIdOrderByCreatedAtDesc(Long userId);
}
