package com.backend.eventsrus.repository;

import com.backend.eventsrus.model.VendorReferral;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VendorReferralRepository extends JpaRepository<VendorReferral, Long> {

    // Explicit underscore-qualified nested-property paths (Referred_Id, not
    // ReferredId) - a field named "referred" needs findByReferred_Id, not
    // findByReferredId, same class of ambiguity that bit SupportTicket's
    // "raisedBy" field earlier this project.
    boolean existsByReferred_Id(Long referredUserId);

    Optional<VendorReferral> findByReferred_Id(Long referredUserId);

    List<VendorReferral> findByReferrer_IdOrderByCreatedAtDesc(Long referrerUserId);

    List<VendorReferral> findAllByOrderByCreatedAtDesc();

    long countByReferrer_Id(Long referrerUserId);
}
