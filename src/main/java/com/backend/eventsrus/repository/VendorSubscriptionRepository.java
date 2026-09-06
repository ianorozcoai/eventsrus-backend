package com.backend.eventsrus.repository;

import com.backend.eventsrus.enums.SubscriptionStatus;
import com.backend.eventsrus.model.VendorSubscription;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VendorSubscriptionRepository extends JpaRepository<VendorSubscription, Long> {

    boolean existsByUserId(Long userId);

    boolean existsByUserIdAndStatusIn(Long userId, Collection<SubscriptionStatus> statuses);

    Optional<VendorSubscription> findByPaypalSubscriptionId(String paypalSubscriptionId);

    Optional<VendorSubscription> findFirstByUserIdOrderByCurrentPeriodStartDesc(Long userId);
}
