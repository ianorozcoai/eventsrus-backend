package com.backend.eventsrus.repository;

import com.backend.eventsrus.model.VendorSocialMediaLink;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VendorSocialMediaLinkRepository extends JpaRepository<VendorSocialMediaLink, Long> {

    List<VendorSocialMediaLink> findByVendorProfileIdOrderByCreatedAtAsc(Long vendorProfileId);

    // The idempotency guard for the one-time Facebook-URL seed - see
    // VendorSocialMediaLinkService#seedFacebookLinkIfNeeded.
    boolean existsByVendorProfileId(Long vendorProfileId);
}
