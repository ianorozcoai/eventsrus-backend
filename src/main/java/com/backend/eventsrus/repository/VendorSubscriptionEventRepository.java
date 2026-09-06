package com.backend.eventsrus.repository;

import com.backend.eventsrus.model.VendorSubscriptionEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VendorSubscriptionEventRepository extends JpaRepository<VendorSubscriptionEvent, Long> {

    boolean existsByPaypalEventId(String paypalEventId);
}
