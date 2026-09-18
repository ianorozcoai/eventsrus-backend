package com.backend.eventsrus.controller;

import com.backend.eventsrus.dto.VendorPackageImageResponse;
import com.backend.eventsrus.service.VendorGalleryPhotoService;
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
 * Standalone storefront photos - not attached to any package (see
 * VendorGalleryPhotoService).
 */
@RestController
@RequestMapping("/api/v1/vendors/me/gallery")
@RequiredArgsConstructor
public class VendorGalleryPhotoController {

    private final VendorGalleryPhotoService vendorGalleryPhotoService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public VendorPackageImageResponse create(
            @RequestPart MultipartFile image, @RequestParam(required = false) String caption,
            Authentication authentication) {
        return vendorGalleryPhotoService.create(authentication.getName(), image, caption);
    }

    @GetMapping
    public List<VendorPackageImageResponse> list(Authentication authentication) {
        return vendorGalleryPhotoService.listForVendor(authentication.getName());
    }

    @DeleteMapping("/{photoId}")
    public void delete(@PathVariable Long photoId, Authentication authentication) {
        vendorGalleryPhotoService.delete(authentication.getName(), photoId);
    }
}
