package com.backend.eventsrus.repository;

import com.backend.eventsrus.model.VendorReview;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VendorReviewRepository extends JpaRepository<VendorReview, Long> {

    Optional<VendorReview> findByBookingId(Long bookingId);

    boolean existsByBookingId(Long bookingId);

    List<VendorReview> findByVendorUserIdAndHiddenFalseOrderByCreatedAtDesc(Long vendorUserId);

    List<VendorReview> findByVendorUserIdOrderByCreatedAtDesc(Long vendorUserId);
}
