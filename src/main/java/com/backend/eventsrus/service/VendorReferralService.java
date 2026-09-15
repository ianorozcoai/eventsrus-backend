package com.backend.eventsrus.service;

import com.backend.eventsrus.dto.AdminReferralResponse;
import com.backend.eventsrus.dto.VendorReferralOverviewResponse;
import com.backend.eventsrus.dto.VendorReferralResponse;
import com.backend.eventsrus.enums.ReferralStatus;
import com.backend.eventsrus.enums.SystemSettingKey;
import com.backend.eventsrus.model.User;
import com.backend.eventsrus.model.VendorProfile;
import com.backend.eventsrus.model.VendorReferral;
import com.backend.eventsrus.repository.UserRepository;
import com.backend.eventsrus.repository.VendorProfileRepository;
import com.backend.eventsrus.repository.VendorReferralRepository;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Value("${app.frontend-base-url}")
    private String frontendBaseUrl;

    private final VendorReferralRepository vendorReferralRepository;
    private final VendorProfileRepository vendorProfileRepository;
    private final UserRepository userRepository;
    private final SystemSettingService systemSettingService;

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
     * Called from UserService#becomeVendor right after a vendor's profile is
     * created. Silently no-ops on any invalid/self/duplicate case - a bad or
     * missing referral code must never block onboarding.
     */
    @Transactional
    public void attribute(User referredUser, String rawReferralCode) {
        if (rawReferralCode == null || rawReferralCode.isBlank()) {
            return;
        }
        if (vendorReferralRepository.existsByReferred_Id(referredUser.getId())) {
            return; // first referral wins - already attributed
        }

        String code = rawReferralCode.trim().toUpperCase(Locale.ROOT);
        VendorProfile referrerProfile = vendorProfileRepository.findByReferralCode(code).orElse(null);
        if (referrerProfile == null) {
            log.warn("Ignoring unknown referral code at onboarding: {}", code);
            return;
        }
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

    @Transactional(readOnly = true)
    public List<AdminReferralResponse> listAllForAdmin() {
        return vendorReferralRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toAdminResponse)
                .toList();
    }

    @Transactional
    public void markPaid(Long referralId) {
        VendorReferral referral = vendorReferralRepository.findById(referralId)
                .orElseThrow(() -> new IllegalStateException("Referral not found: " + referralId));
        referral.setStatus(ReferralStatus.COMMISSION_PAID);
        referral.setPaidAt(Instant.now());
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
                .build();
    }
}
