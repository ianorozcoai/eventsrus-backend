package com.backend.eventsrus.service;

import com.backend.eventsrus.dto.VendorPaymentMethodResponse;
import com.backend.eventsrus.enums.PaymentMethodStatus;
import com.backend.eventsrus.exception.InvalidFileTypeException;
import com.backend.eventsrus.model.User;
import com.backend.eventsrus.model.VendorPaymentMethod;
import com.backend.eventsrus.model.VendorProfile;
import com.backend.eventsrus.repository.UserRepository;
import com.backend.eventsrus.repository.VendorPaymentMethodRepository;
import com.backend.eventsrus.repository.VendorProfileRepository;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class VendorPaymentMethodService {

    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of("image/png", "image/jpeg");

    private final VendorPaymentMethodRepository vendorPaymentMethodRepository;
    private final VendorProfileRepository vendorProfileRepository;
    private final UserRepository userRepository;
    private final S3UploadService s3UploadService;

    @Transactional
    public VendorPaymentMethodResponse create(String vendorEmail, String label, MultipartFile qrImage) {
        if (qrImage == null || qrImage.isEmpty()) {
            throw new InvalidFileTypeException("A QR code image is required");
        }
        requireImage(qrImage);

        VendorProfile profile = requireProfile(vendorEmail);
        String qrImageUrl = s3UploadService
                .upload(qrImage, keyPrefix(profile.getUser().getId()), S3UploadService.Visibility.PUBLIC)
                .url();

        // Admin approval is disabled for now (no review screen exists yet -
        // PaymentMethodStatus.PENDING/REJECTED are kept, just unused here
        // until that ships) - auto-approve on upload instead so a vendor's
        // payment methods show on their storefront immediately. See
        // V32__auto_approve_existing_payment_methods.sql for the one-time
        // backfill of anything that was already stuck PENDING.
        VendorPaymentMethod saved = vendorPaymentMethodRepository.save(VendorPaymentMethod.builder()
                .vendorProfile(profile)
                .label(label)
                .qrImageUrl(qrImageUrl)
                .status(PaymentMethodStatus.APPROVED)
                .build());
        return toResponse(saved);
    }

    @Transactional
    public void delete(String vendorEmail, Long paymentMethodId) {
        VendorProfile profile = requireProfile(vendorEmail);
        VendorPaymentMethod method = vendorPaymentMethodRepository.findById(paymentMethodId)
                .orElseThrow(() -> new IllegalStateException("Payment method not found: " + paymentMethodId));
        if (!method.getVendorProfile().getId().equals(profile.getId())) {
            throw new IllegalStateException("Payment method does not belong to the authenticated vendor");
        }
        vendorPaymentMethodRepository.delete(method);
    }

    @Transactional(readOnly = true)
    public List<VendorPaymentMethodResponse> listForVendor(String vendorEmail) {
        VendorProfile profile = requireProfile(vendorEmail);
        return vendorPaymentMethodRepository.findByVendorProfileIdOrderByCreatedAtDesc(profile.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<VendorPaymentMethodResponse> listApprovedForStorefront(Long vendorProfileId) {
        return vendorPaymentMethodRepository
                .findByVendorProfileIdAndStatusOrderByCreatedAtDesc(vendorProfileId, PaymentMethodStatus.APPROVED)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private void requireImage(MultipartFile file) {
        if (!ALLOWED_IMAGE_TYPES.contains(file.getContentType())) {
            throw new InvalidFileTypeException("Only PNG or JPEG images are accepted for this upload");
        }
    }

    private VendorProfile requireProfile(String vendorEmail) {
        User user = userRepository.findByEmail(vendorEmail)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + vendorEmail));
        return vendorProfileRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalStateException("Vendor profile not found for user: " + user.getId()));
    }

    private VendorPaymentMethodResponse toResponse(VendorPaymentMethod method) {
        return VendorPaymentMethodResponse.builder()
                .id(method.getId())
                .label(method.getLabel())
                .qrImageUrl(method.getQrImageUrl())
                .status(method.getStatus())
                .build();
    }

    private String keyPrefix(Long userId) {
        return "vendors/" + userId + "/payment-method";
    }
}
