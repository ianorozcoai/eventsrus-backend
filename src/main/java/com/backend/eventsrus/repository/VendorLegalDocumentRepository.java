package com.backend.eventsrus.repository;

import com.backend.eventsrus.model.VendorLegalDocument;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VendorLegalDocumentRepository extends JpaRepository<VendorLegalDocument, Long> {

    List<VendorLegalDocument> findByVendorProfileIdOrderByCreatedAtDesc(Long vendorProfileId);
}
