package com.backend.eventsrus.repository;

import com.backend.eventsrus.enums.PaymentMethodStatus;
import com.backend.eventsrus.model.VendorPaymentMethod;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VendorPaymentMethodRepository extends JpaRepository<VendorPaymentMethod, Long> {

    List<VendorPaymentMethod> findByVendorProfileIdOrderByCreatedAtDesc(Long vendorProfileId);

    List<VendorPaymentMethod> findByVendorProfileIdAndStatusOrderByCreatedAtDesc(
            Long vendorProfileId, PaymentMethodStatus status);
}
