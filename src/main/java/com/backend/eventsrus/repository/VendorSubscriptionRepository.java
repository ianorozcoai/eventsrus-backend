package com.backend.eventsrus.repository;

import com.backend.eventsrus.enums.BillingSource;
import com.backend.eventsrus.enums.SubscriptionStatus;
import com.backend.eventsrus.model.VendorSubscription;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VendorSubscriptionRepository extends JpaRepository<VendorSubscription, Long> {

    boolean existsByUserId(Long userId);

    boolean existsByUserIdAndStatusIn(Long userId, Collection<SubscriptionStatus> statuses);

    boolean existsByUserIdAndBillingSourceAndStatusIn(
            Long userId, BillingSource billingSource, Collection<SubscriptionStatus> statuses);

    Optional<VendorSubscription> findByPaypalSubscriptionId(String paypalSubscriptionId);

    // Plain "ORDER BY current_period_start DESC" would let Postgres' default
    // NULLS FIRST silently win here - a row whose period was cleared back to
    // null (rejectGcashPayment - see VendorSubscriptionService) would always
    // outrank a real, more recent row from a different billing source (e.g.
    // the vendor pays via PayPal after an earlier GCash rejection), making
    // VendorPlanService#getEffectivePlan wrongly report no live plan. Hence
    // the explicit query and "nulls last" instead of a derived method name.
    @Query("select vs from VendorSubscription vs where vs.user.id = :userId order by vs.currentPeriodStart desc nulls last")
    List<VendorSubscription> findByUserIdOrderByCurrentPeriodStartDescNullsLast(@Param("userId") Long userId, Pageable pageable);

    default Optional<VendorSubscription> findFirstByUserIdOrderByCurrentPeriodStartDesc(Long userId) {
        List<VendorSubscription> rows = findByUserIdOrderByCurrentPeriodStartDescNullsLast(userId, PageRequest.of(0, 1));
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    // GCash's manual flow - find an existing row to update in place, both
    // for the vendor's own re-submission after a rejection and for the
    // admin's approve/reject actions (see VendorSubscriptionService).
    Optional<VendorSubscription> findFirstByUserIdAndBillingSourceOrderByCreatedAtDesc(
            Long userId, BillingSource billingSource);

    // The admin module's GCash queue - every submission regardless of
    // status, partitioned into Pending/Verified/Rejected tabs web-side.
    List<VendorSubscription> findByBillingSourceOrderByCreatedAtDesc(BillingSource billingSource);
}
