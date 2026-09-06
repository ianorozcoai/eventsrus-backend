package com.backend.eventsrus.repository;

import com.backend.eventsrus.model.VendorBillingHistoryEntry;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VendorBillingHistoryEntryRepository extends JpaRepository<VendorBillingHistoryEntry, Long> {

    List<VendorBillingHistoryEntry> findByVendorSubscription_User_IdOrderByOccurredAtDesc(Long userId);

    boolean existsByPaypalTransactionId(String paypalTransactionId);
}
