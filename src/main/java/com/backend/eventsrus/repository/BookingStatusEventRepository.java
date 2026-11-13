package com.backend.eventsrus.repository;

import com.backend.eventsrus.model.BookingStatusEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingStatusEventRepository extends JpaRepository<BookingStatusEvent, Long> {

    List<BookingStatusEvent> findByBookingIdOrderByCreatedAtAsc(Long bookingId);

    /**
     * When each of a supplier's "counted" bookings was counted, oldest first, at most {@code limit} rows.
     * A booking counts once the planner has submitted payment (PAYMENT_SUBMITTED, or BOOKED reached
     * without it), counted at the earliest of those moments, once per booking. A booking whose payment
     * the supplier rejected (current status PAYMENT_REJECTED) or that was declined does not count;
     * one cancelled later still counts. Used for the free-until-bookings offer.
     */
    @Query(value = "SELECT min(h.created_at) AS counted_at FROM booking_status_history h"
            + " JOIN bookings b ON b.id = h.booking_id"
            + " WHERE b.vendor_user_id = :vendorUserId"
            + " AND h.to_status IN ('PAYMENT_SUBMITTED', 'BOOKED')"
            + " AND b.status NOT IN ('PAYMENT_REJECTED', 'DECLINED')"
            + " GROUP BY h.booking_id ORDER BY counted_at ASC LIMIT :limit", nativeQuery = true)
    List<Object> countedBookingTimes(@Param("vendorUserId") Long vendorUserId, @Param("limit") int limit);
}
