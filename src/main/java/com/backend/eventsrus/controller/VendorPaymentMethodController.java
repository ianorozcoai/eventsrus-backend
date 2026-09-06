package com.backend.eventsrus.controller;

import com.backend.eventsrus.dto.VendorPaymentMethodRequest;
import com.backend.eventsrus.dto.VendorPaymentMethodResponse;
import com.backend.eventsrus.service.VendorPaymentMethodService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/vendors/me/payment-methods")
@RequiredArgsConstructor
public class VendorPaymentMethodController {

    private final VendorPaymentMethodService vendorPaymentMethodService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public VendorPaymentMethodResponse create(
            @Valid @ModelAttribute VendorPaymentMethodRequest request,
            @RequestPart MultipartFile qrImage,
            Authentication authentication) {
        return vendorPaymentMethodService.create(authentication.getName(), request.getLabel(), qrImage);
    }

    @GetMapping
    public List<VendorPaymentMethodResponse> list(Authentication authentication) {
        return vendorPaymentMethodService.listForVendor(authentication.getName());
    }

    @DeleteMapping("/{paymentMethodId}")
    public void delete(@PathVariable Long paymentMethodId, Authentication authentication) {
        vendorPaymentMethodService.delete(authentication.getName(), paymentMethodId);
    }
}
