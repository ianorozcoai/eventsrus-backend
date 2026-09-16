package com.backend.eventsrus.repository;

import com.backend.eventsrus.enums.BookingStatus;
import com.backend.eventsrus.model.Booking;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByEventIdOrderByCreatedAtDesc(Long eventId);

    List<Booking> findByVendorUserIdOrderByEventDatetimeAsc(Long vendorUserId);

    List<Booking> findByPlannerUserIdOrderByEventDatetimeAsc(Long plannerUserId);

    List<Booking> findByVendorUserIdAndStatus(Long vendorUserId, BookingStatus status);

    long countByVendorUserIdAndStatus(Long vendorUserId, BookingStatus status);

    boolean existsByQuotationId(Long quotationId);

    // Powers the vendor nav / planner event-tab "unseen" badges (see
    // BadgeService) - anything touched since the viewer's last visit,
    // regardless of which side caused the change.
    @Query("SELECT b.id FROM Booking b WHERE b.vendorUser.id = :vendorUserId AND b.updatedAt > :after")
    List<Long> findIdsByVendorUserIdAndUpdatedAtAfter(@Param("vendorUserId") Long vendorUserId, @Param("after") Instant after);

    @Query("SELECT b.id FROM Booking b WHERE b.event.id = :eventId AND b.updatedAt > :after")
    List<Long> findIdsByEventIdAndUpdatedAtAfter(@Param("eventId") Long eventId, @Param("after") Instant after);
}
