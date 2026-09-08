package com.backend.eventsrus.controller;

import com.backend.eventsrus.dto.VendorPackageImageResponse;
import com.backend.eventsrus.service.VendorPackageImageService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Real photos per package - shown in the vendor's own Manage Packages (edit
 * mode) and, combined across every package, as the storefront's Gallery
 * section (see VendorPackageImageService).
 */
@RestController
@RequestMapping("/api/v1/vendors/me/packages/{packageId}/images")
@RequiredArgsConstructor
public class VendorPackageImageController {

    private final VendorPackageImageService vendorPackageImageService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public VendorPackageImageResponse create(
            @PathVariable Long packageId,
            @RequestPart MultipartFile image,
            @RequestParam(required = false) String caption,
            Authentication authentication) {
        return vendorPackageImageService.create(authentication.getName(), packageId, image, caption);
    }

    @GetMapping
    public List<VendorPackageImageResponse> list(@PathVariable Long packageId) {
        return vendorPackageImageService.listForPackage(packageId);
    }

    @DeleteMapping("/{imageId}")
    public void delete(@PathVariable Long packageId, @PathVariable Long imageId, Authentication authentication) {
        vendorPackageImageService.delete(authentication.getName(), packageId, imageId);
    }
}
