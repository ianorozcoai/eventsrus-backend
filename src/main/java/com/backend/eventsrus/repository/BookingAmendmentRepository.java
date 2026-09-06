package com.backend.eventsrus.repository;

import com.backend.eventsrus.enums.AmendmentStatus;
import com.backend.eventsrus.model.BookingAmendment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingAmendmentRepository extends JpaRepository<BookingAmendment, Long> {

    List<BookingAmendment> findByBookingIdOrderByCreatedAtDesc(Long bookingId);

    boolean existsByBookingIdAndStatus(Long bookingId, AmendmentStatus status);
}
