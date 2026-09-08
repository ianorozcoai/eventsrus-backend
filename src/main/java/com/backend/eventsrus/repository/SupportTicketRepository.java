package com.backend.eventsrus.repository;

import com.backend.eventsrus.enums.Role;
import com.backend.eventsrus.enums.TicketStatus;
import com.backend.eventsrus.model.SupportTicket;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {

    List<SupportTicket> findByRaisedByIdOrderByCreatedAtDesc(Long userId);

    // Admin's "Vendor complaints" / "Planner complaints" split - every
    // ticket raised by a user of that role, not just the caller's own.
    List<SupportTicket> findByRaisedBy_RoleOrderByCreatedAtDesc(Role role);

    List<SupportTicket> findAllByOrderByCreatedAtDesc();

    // Admin dashboard counts - total vendor-raised tickets, and "new"
    // (still OPEN, i.e. nobody's replied/actioned it yet) counts per role.
    long countByRaisedBy_Role(Role role);

    long countByRaisedBy_RoleAndStatus(Role role, TicketStatus status);
}
