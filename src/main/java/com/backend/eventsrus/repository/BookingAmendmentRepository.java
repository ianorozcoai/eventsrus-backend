package com.backend.eventsrus.repository;

import com.backend.eventsrus.enums.AmendmentStatus;
import com.backend.eventsrus.model.BookingAmendment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingAmendmentRepository extends JpaRepository<BookingAmendment, Long> {

    List<BookingAmendment> findByBookingIdOrderByCreatedAtDesc(Long bookingId);

    boolean existsByBookingIdAndStatus(Long bookingId, AmendmentStatus status);

    // A pending amendment always needs the other side's response, no matter
    // how old it is - unlike a plain status change, "seen it but haven't
    // acted yet" shouldn't make its badge disappear. Powers the vendor nav /
    // planner event-tab "unseen" Bookings badges (see BadgeService),
    // alongside the plain updated_at check that catches everything else
    // (proposing an amendment doesn't itself touch the booking row).
    @Query("SELECT DISTINCT a.booking.id FROM BookingAmendment a "
            + "WHERE a.booking.vendorUser.id = :vendorUserId AND a.status = 'PENDING' "
            + "AND a.proposedBy.id <> :vendorUserId")
    List<Long> findPendingBookingIdsAwaitingVendor(@Param("vendorUserId") Long vendorUserId);

    @Query("SELECT DISTINCT a.booking.id FROM BookingAmendment a "
            + "WHERE a.booking.event.id = :eventId AND a.booking.plannerUser.id = :plannerUserId "
            + "AND a.status = 'PENDING' AND a.proposedBy.id <> :plannerUserId")
    List<Long> findPendingBookingIdsAwaitingPlannerForEvent(
            @Param("plannerUserId") Long plannerUserId, @Param("eventId") Long eventId);
}
