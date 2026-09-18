package com.backend.eventsrus.service;

import com.backend.eventsrus.dto.VendorPackageImageResponse;
import com.backend.eventsrus.enums.SystemSettingKey;
import com.backend.eventsrus.exception.InvalidFileTypeException;
import com.backend.eventsrus.model.User;
import com.backend.eventsrus.model.VendorGalleryPhoto;
import com.backend.eventsrus.model.VendorProfile;
import com.backend.eventsrus.repository.UserRepository;
import com.backend.eventsrus.repository.VendorGalleryPhotoRepository;
import com.backend.eventsrus.repository.VendorProfileRepository;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Standalone storefront photos - not attached to any package (see
 * VendorGalleryPhoto). Combined with every package's own photos to build the
 * storefront's single Gallery section (see VendorDirectoryService), so a
 * vendor whose packages don't have much to hang photos off of can still
 * showcase sample work. Public bucket, direct URL - same reasoning as
 * VendorPackageImageService: marketing photos, not a privacy-sensitive
 * document.
 */
@Service
@RequiredArgsConstructor
public class VendorGalleryPhotoService {

    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of("image/png", "image/jpeg");

    private final VendorGalleryPhotoRepository vendorGalleryPhotoRepository;
    private final VendorProfileRepository vendorProfileRepository;
    private final UserRepository userRepository;
    private final S3UploadService s3UploadService;
    private final SystemSettingService systemSettingService;

    @Transactional
    public VendorPackageImageResponse create(String vendorEmail, MultipartFile image, String caption) {
        if (image == null || image.isEmpty()) {
            throw new InvalidFileTypeException("An image file is required");
        }
        if (!ALLOWED_IMAGE_TYPES.contains(image.getContentType())) {
            throw new InvalidFileTypeException("Only PNG or JPEG images are accepted for gallery photos");
        }
        VendorProfile profile = requireProfile(vendorEmail);

        int limit = systemSettingService.getInt(SystemSettingKey.VENDOR_GALLERY_PHOTO_LIMIT);
        if (vendorGalleryPhotoRepository.countByVendorProfileId(profile.getId()) >= limit) {
            throw new InvalidFileTypeException("You've reached the maximum of " + limit + " gallery photos.");
        }

        String imageUrl = s3UploadService
                .upload(image, keyPrefix(profile.getUser().getId()), S3UploadService.Visibility.PUBLIC)
                .url();

        VendorGalleryPhoto saved = vendorGalleryPhotoRepository.save(VendorGalleryPhoto.builder()
                .vendorProfile(profile)
                .imageUrl(imageUrl)
                .caption(caption)
                .build());
        return toResponse(saved);
    }

    @Transactional
    public void delete(String vendorEmail, Long photoId) {
        VendorProfile profile = requireProfile(vendorEmail);
        VendorGalleryPhoto photo = vendorGalleryPhotoRepository.findById(photoId)
                .orElseThrow(() -> new IllegalStateException("Gallery photo not found: " + photoId));
        if (!photo.getVendorProfile().getId().equals(profile.getId())) {
            throw new IllegalStateException("Gallery photo does not belong to the authenticated vendor");
        }
        vendorGalleryPhotoRepository.delete(photo);
    }

    @Transactional(readOnly = true)
    public List<VendorPackageImageResponse> listForVendor(String vendorEmail) {
        VendorProfile profile = requireProfile(vendorEmail);
        return listForVendorProfile(profile.getId());
    }

    /** Every standalone gallery photo for a vendor, newest first - also used by VendorDirectoryService to build the storefront Gallery. */
    @Transactional(readOnly = true)
    public List<VendorPackageImageResponse> listForVendorProfile(Long vendorProfileId) {
        return vendorGalleryPhotoRepository.findByVendorProfileIdOrderByCreatedAtDesc(vendorProfileId).stream()
                .map(this::toResponse)
                .toList();
    }

    private VendorProfile requireProfile(String vendorEmail) {
        User user = userRepository.findByEmail(vendorEmail)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + vendorEmail));
        return vendorProfileRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalStateException("Vendor profile not found for user: " + user.getId()));
    }

    private VendorPackageImageResponse toResponse(VendorGalleryPhoto photo) {
        return VendorPackageImageResponse.builder()
                .id(photo.getId())
                .imageUrl(photo.getImageUrl())
                .caption(photo.getCaption())
                .createdAt(photo.getCreatedAt())
                .build();
    }

    private String keyPrefix(Long userId) {
        return "vendors/" + userId + "/gallery/image";
    }
}
