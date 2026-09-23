package com.backend.eventsrus.repository;

import com.backend.eventsrus.model.VendorBillingHistoryEntry;
import com.backend.eventsrus.model.VendorSubscription;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VendorBillingHistoryEntryRepository extends JpaRepository<VendorBillingHistoryEntry, Long> {

    List<VendorBillingHistoryEntry> findByVendorSubscription_User_IdOrderByOccurredAtDesc(Long userId);

    boolean existsByPaypalTransactionId(String paypalTransactionId);

    // Clears out the temporary-access "free grant" entry recordFreeGrant
    // creates on GCash submission (see VendorSubscriptionService#
    // submitGcashPayment) once that same subscription's real payment is
    // verified - see #verifyGcashPayment - so the vendor's billing history
    // only ever shows the one real, amount-bearing record for it.
    void deleteByVendorSubscriptionAndAmountIsNull(VendorSubscription subscription);
}
