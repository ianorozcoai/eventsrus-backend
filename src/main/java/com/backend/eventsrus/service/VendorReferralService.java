package com.backend.eventsrus.service;

import com.backend.eventsrus.dto.AdminReferralResponse;
import com.backend.eventsrus.dto.VendorReferralOverviewResponse;
import com.backend.eventsrus.dto.VendorReferralResponse;
import com.backend.eventsrus.enums.ReferralStatus;
import com.backend.eventsrus.enums.SystemSettingKey;
import com.backend.eventsrus.exception.InvalidReferralCodeException;
import com.backend.eventsrus.model.User;
import com.backend.eventsrus.model.VendorProfile;
import com.backend.eventsrus.model.VendorReferral;
import com.backend.eventsrus.repository.UserRepository;
import com.backend.eventsrus.repository.VendorProfileRepository;
import com.backend.eventsrus.repository.VendorReferralRepository;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Vendor-refers-vendor program: a vendor shares their referral link, a new
 * vendor onboards through it (see UserService#becomeVendor -&gt; attribute),
 * and once that referred vendor's subscription is backed by a real PayPal
 * payment (PayPalWebhookService -&gt; convertIfPending) a fixed one-time
 * commission is owed. Payout itself is manual - markPaid is the only thing
 * that ever moves a referral to COMMISSION_PAID, called by an admin once the
 * money has actually been sent.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VendorReferralService {

    // Referral codes deliberately exclude 0/O/1/I - easy to mis-key when
    // read aloud or copied from a low-res screenshot.
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 8;

    // Matches VendorSubscriptionService's own PAYMENT_SCREENSHOT_URL_TTL for
    // the same purpose (a private-document link sitting on a review page).
    private static final Duration PAYMENT_PROOF_URL_TTL = Duration.ofHours(1);

    @Value("${app.frontend-base-url}")
    private String frontendBaseUrl;

    private final VendorReferralRepository vendorReferralRepository;
    private final VendorProfileRepository vendorProfileRepository;
    private final UserRepository userRepository;
    private final SystemSettingService systemSettingService;
    private final S3UploadService s3UploadService;

    public String generateUniqueReferralCode() {
        SecureRandom random = new SecureRandom();
        String candidate;
        do {
            StringBuilder sb = new StringBuilder(CODE_LENGTH);
            for (int i = 0; i < CODE_LENGTH; i++) {
                sb.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
            }
            candidate = sb.toString();
        } while (vendorProfileRepository.existsByReferralCode(candidate));
        return candidate;
    }

    /**
     * A blank code is fine (referral is optional); an already-attributed
     * referred user is a silent no-op too (re-submitting onboarding must
     * stay safe, and first referral always wins). A non-blank code that
     * doesn't resolve to any vendor is the one case that's a real error -
     * see requireValidReferralCodeIfPresent, called early in
     * UserService#becomeVendor so a typo'd code fails fast, before any
     * uploads happen, rather than surfacing all the way down here.
     */
    @Transactional
    public void attribute(User referredUser, String rawReferralCode) {
        if (rawReferralCode == null || rawReferralCode.isBlank()) {
            return;
        }
        if (vendorReferralRepository.existsByReferred_Id(referredUser.getId())) {
            return; // first referral wins - already attributed
        }

        VendorProfile referrerProfile = findByReferralCode(rawReferralCode)
                .orElseThrow(() -> new InvalidReferralCodeException(
                        "We couldn't find a vendor with referral code \"" + rawReferralCode.trim() + "\"."));
        if (referrerProfile.getUser().getId().equals(referredUser.getId())) {
            log.warn("Ignoring self-referral attempt for user {}", referredUser.getId());
            return;
        }

        vendorReferralRepository.save(VendorReferral.builder()
                .referrer(referrerProfile.getUser())
                .referred(referredUser)
                .status(ReferralStatus.PENDING)
                .build());
    }

    /**
     * Fails fast on a typo'd/unknown referral code at the very start of
     * onboarding, before any files are uploaded - see UserService#becomeVendor.
     * A blank code is fine (referral is optional).
     */
    @Transactional(readOnly = true)
    public void requireValidReferralCodeIfPresent(String rawReferralCode) {
        if (rawReferralCode == null || rawReferralCode.isBlank()) {
            return;
        }
        if (findByReferralCode(rawReferralCode).isEmpty()) {
            throw new InvalidReferralCodeException(
                    "We couldn't find a vendor with referral code \"" + rawReferralCode.trim() + "\".");
        }
    }

    /**
     * Admin-only fix for a referral that was never attributed at signup time
     * (e.g. the referred vendor forgot to use the link) - see
     * AdminVendorController. Unlike #attribute, every failure here is a real
     * error: an admin's mistake should surface, not silently no-op. Lets the
     * admin set the resulting status directly (Pending / Converted / Already
     * Paid) since the referred vendor may well have already paid before this
     * gets fixed - see the CONVERTED/COMMISSION_PAID branch below.
     */
    @Transactional
    public void createManualReferral(
            Long referredUserId, String referrerCode, ReferralStatus status, BigDecimal commissionAmount) {
        User referredUser = userRepository.findById(referredUserId)
                .orElseThrow(() -> new IllegalStateException("Vendor not found: " + referredUserId));
        if (vendorReferralRepository.existsByReferred_Id(referredUserId)) {
            throw new IllegalStateException("This vendor is already attributed to a referrer.");
        }
        VendorProfile referrerProfile = findByReferralCode(referrerCode)
                .orElseThrow(() -> new InvalidReferralCodeException(
                        "We couldn't find a vendor with referral code \"" + referrerCode + "\"."));
        if (referrerProfile.getUser().getId().equals(referredUserId)) {
            throw new IllegalStateException("A vendor can't be tagged as referred by themselves.");
        }

        var referral = VendorReferral.builder()
                .referrer(referrerProfile.getUser())
                .referred(referredUser)
                .status(status);
        if (status == ReferralStatus.CONVERTED || status == ReferralStatus.COMMISSION_PAID) {
            referral.commissionAmount(commissionAmount != null
                    ? commissionAmount
                    : systemSettingService.getBigDecimal(SystemSettingKey.REFERRAL_COMMISSION_AMOUNT));
            referral.convertedAt(Instant.now());
        }
        if (status == ReferralStatus.COMMISSION_PAID) {
            referral.paidAt(Instant.now());
        }
        vendorReferralRepository.save(referral.build());
    }

    private Optional<VendorProfile> findByReferralCode(String rawReferralCode) {
        if (rawReferralCode == null || rawReferralCode.isBlank()) {
            return Optional.empty();
        }
        return vendorProfileRepository.findByReferralCode(rawReferralCode.trim().toUpperCase(Locale.ROOT));
    }

    /**
     * Called from PayPalWebhookService on every PAYMENT.SALE.COMPLETED. A
     * no-op unless there's a still-PENDING referral for this vendor, so
     * renewal payments (which fire the same event) never re-convert or
     * re-award a commission.
     */
    @Transactional
    public void convertIfPending(Long referredUserId) {
        vendorReferralRepository.findByReferred_Id(referredUserId).ifPresent(referral -> {
            if (referral.getStatus() != ReferralStatus.PENDING) {
                return;
            }
            referral.setStatus(ReferralStatus.CONVERTED);
            referral.setCommissionAmount(systemSettingService.getBigDecimal(SystemSettingKey.REFERRAL_COMMISSION_AMOUNT));
            referral.setConvertedAt(Instant.now());
            vendorReferralRepository.save(referral);
        });
    }

    @Transactional(readOnly = true)
    public VendorReferralOverviewResponse getOverview(String vendorEmail) {
        User user = userRepository.findByEmail(vendorEmail)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + vendorEmail));
        VendorProfile profile = vendorProfileRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalStateException("Vendor profile not found for user: " + user.getId()));

        List<VendorReferral> referrals = vendorReferralRepository.findByReferrer_IdOrderByCreatedAtDesc(user.getId());

        return VendorReferralOverviewResponse.builder()
                .referralCode(profile.getReferralCode())
                .referralLink(frontendBaseUrl + "/vendor/?ref=" + profile.getReferralCode())
                .totalPendingCommission(sumByStatus(referrals, ReferralStatus.CONVERTED))
                .totalPaidCommission(sumByStatus(referrals, ReferralStatus.COMMISSION_PAID))
                .referrals(referrals.stream().map(this::toResponse).toList())
                .build();
    }

    /** Used by UserService#listVendorsForAdmin for the vendors table's referral-count column. */
    @Transactional(readOnly = true)
    public long countReferralsMade(Long vendorUserId) {
        return vendorReferralRepository.countByReferrer_Id(vendorUserId);
    }

    @Transactional(readOnly = true)
    public List<AdminReferralResponse> listAllForAdmin() {
        return vendorReferralRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toAdminResponse)
                .toList();
    }

    /** Whichever referral (if any) this vendor was the referred party on - for the admin vendor detail page. */
    @Transactional(readOnly = true)
    public Optional<AdminReferralResponse> findForReferredVendor(Long referredUserId) {
        return vendorReferralRepository.findByReferred_Id(referredUserId).map(this::toAdminResponse);
    }

    /**
     * remarks and proof are both optional - an admin can mark paid with
     * neither if they don't have a screenshot handy. proof (a transfer
     * screenshot or deposit slip) is uploaded the same way a GCash payment
     * screenshot is - see VendorSubscriptionService#submitGcashPayment.
     */
    @Transactional
    public void markPaid(Long referralId, String remarks, MultipartFile proof) {
        VendorReferral referral = vendorReferralRepository.findById(referralId)
                .orElseThrow(() -> new IllegalStateException("Referral not found: " + referralId));
        referral.setStatus(ReferralStatus.COMMISSION_PAID);
        referral.setPaidAt(Instant.now());
        referral.setPaymentRemarks(remarks != null && !remarks.isBlank() ? remarks : null);
        if (proof != null && !proof.isEmpty()) {
            String key = s3UploadService
                    .upload(proof, "referrals/" + referralId + "/payment-proof", S3UploadService.Visibility.PRIVATE)
                    .key();
            referral.setPaymentProofKey(key);
            referral.setPaymentProofUploadedAt(Instant.now());
        }
        vendorReferralRepository.save(referral);
    }

    private BigDecimal sumByStatus(List<VendorReferral> referrals, ReferralStatus status) {
        return referrals.stream()
                .filter(r -> r.getStatus() == status)
                .map(VendorReferral::getCommissionAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private VendorReferralResponse toResponse(VendorReferral referral) {
        VendorProfile referredProfile = vendorProfileRepository.findByUserId(referral.getReferred().getId()).orElse(null);
        return VendorReferralResponse.builder()
                .id(referral.getId())
                .referredBusinessName(referredProfile != null && referredProfile.getBusinessName() != null
                        ? referredProfile.getBusinessName()
                        : referral.getReferred().getFirstName())
                .status(referral.getStatus())
                .commissionAmount(referral.getCommissionAmount())
                .createdAt(referral.getCreatedAt())
                .convertedAt(referral.getConvertedAt())
                .paidAt(referral.getPaidAt())
                .paymentRemarks(referral.getPaymentRemarks())
                .paymentProofUrl(s3UploadService.presignedUrl(referral.getPaymentProofKey(), PAYMENT_PROOF_URL_TTL))
                .build();
    }

    private AdminReferralResponse toAdminResponse(VendorReferral referral) {
        VendorProfile referrerProfile = vendorProfileRepository.findByUserId(referral.getReferrer().getId()).orElse(null);
        VendorProfile referredProfile = vendorProfileRepository.findByUserId(referral.getReferred().getId()).orElse(null);
        return AdminReferralResponse.builder()
                .id(referral.getId())
                .referrerBusinessName(referrerProfile != null ? referrerProfile.getBusinessName() : null)
                .referrerEmail(referral.getReferrer().getEmail())
                .referredBusinessName(referredProfile != null ? referredProfile.getBusinessName() : null)
                .referredEmail(referral.getReferred().getEmail())
                .status(referral.getStatus())
                .commissionAmount(referral.getCommissionAmount())
                .createdAt(referral.getCreatedAt())
                .convertedAt(referral.getConvertedAt())
                .paidAt(referral.getPaidAt())
                .paymentRemarks(referral.getPaymentRemarks())
                .paymentProofUrl(s3UploadService.presignedUrl(referral.getPaymentProofKey(), PAYMENT_PROOF_URL_TTL))
                .build();
    }
}
