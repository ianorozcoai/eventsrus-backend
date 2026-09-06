package com.backend.eventsrus.controller;

import com.backend.eventsrus.dto.VendorPackageRequest;
import com.backend.eventsrus.dto.VendorPackageResponse;
import com.backend.eventsrus.service.VendorPackageService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/vendors/me/packages")
@RequiredArgsConstructor
public class VendorPackageController {

    private final VendorPackageService vendorPackageService;

    @PostMapping
    public VendorPackageResponse create(@Valid @RequestBody VendorPackageRequest request, Authentication authentication) {
        return vendorPackageService.createPackage(authentication.getName(), request);
    }

    @GetMapping
    public List<VendorPackageResponse> list(Authentication authentication) {
        return vendorPackageService.listForVendor(authentication.getName());
    }

    @PutMapping("/{packageId}")
    public VendorPackageResponse update(
            @PathVariable Long packageId, @Valid @RequestBody VendorPackageRequest request, Authentication authentication) {
        return vendorPackageService.updatePackage(authentication.getName(), packageId, request);
    }

    @PutMapping("/{packageId}/active")
    public VendorPackageResponse setActive(
            @PathVariable Long packageId, @RequestBody java.util.Map<String, Boolean> body, Authentication authentication) {
        return vendorPackageService.setActive(authentication.getName(), packageId, Boolean.TRUE.equals(body.get("active")));
    }

    @DeleteMapping("/{packageId}")
    public void delete(@PathVariable Long packageId, Authentication authentication) {
        vendorPackageService.deletePackage(authentication.getName(), packageId);
    }
}
