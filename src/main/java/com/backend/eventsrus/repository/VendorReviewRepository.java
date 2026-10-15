package com.backend.eventsrus.repository;

import com.backend.eventsrus.model.VendorReview;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VendorReviewRepository extends JpaRepository<VendorReview, Long> {

    Optional<VendorReview> findByBookingId(Long bookingId);

    /** Account deletion: blank the comment of every review this planner wrote. */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(
            "update VendorReview r set r.comment = null "
                    + "where r.booking.id in (select b.id from Booking b where b.plannerUser.id = :userId)")
    int scrubCommentsWrittenBy(@org.springframework.data.repository.query.Param("userId") Long userId);

    boolean existsByBookingId(Long bookingId);

    List<VendorReview> findByVendorUserIdAndHiddenFalseOrderByCreatedAtDesc(Long vendorUserId);

    List<VendorReview> findByVendorUserIdOrderByCreatedAtDesc(Long vendorUserId);
}
