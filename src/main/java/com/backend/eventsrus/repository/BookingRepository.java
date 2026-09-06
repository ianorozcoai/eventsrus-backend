package com.backend.eventsrus.repository;

import com.backend.eventsrus.enums.BookingStatus;
import com.backend.eventsrus.model.Booking;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByEventIdOrderByCreatedAtDesc(Long eventId);

    List<Booking> findByVendorUserIdOrderByEventDatetimeAsc(Long vendorUserId);

    List<Booking> findByPlannerUserIdOrderByEventDatetimeAsc(Long plannerUserId);

    List<Booking> findByVendorUserIdAndStatus(Long vendorUserId, BookingStatus status);

    long countByVendorUserIdAndStatus(Long vendorUserId, BookingStatus status);

    boolean existsByQuotationId(Long quotationId);
}
