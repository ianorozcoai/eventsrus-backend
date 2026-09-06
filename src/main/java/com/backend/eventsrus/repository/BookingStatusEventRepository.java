package com.backend.eventsrus.repository;

import com.backend.eventsrus.model.BookingStatusEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingStatusEventRepository extends JpaRepository<BookingStatusEvent, Long> {

    List<BookingStatusEvent> findByBookingIdOrderByCreatedAtAsc(Long bookingId);
}
