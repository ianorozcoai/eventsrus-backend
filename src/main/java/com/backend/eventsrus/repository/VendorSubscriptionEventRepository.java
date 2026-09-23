package com.backend.eventsrus.repository;

import com.backend.eventsrus.model.VendorSubscriptionEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VendorSubscriptionEventRepository extends JpaRepository<VendorSubscriptionEvent, Long> {

    boolean existsByPaypalEventId(String paypalEventId);

    // The admin module's per-vendor GCash review timeline - see
    // VendorSubscriptionService#listGcashPayments.
    List<VendorSubscriptionEvent> findByVendorSubscription_IdOrderByOccurredAtDesc(Long vendorSubscriptionId);
}
