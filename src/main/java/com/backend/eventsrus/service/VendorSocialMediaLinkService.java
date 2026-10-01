package com.backend.eventsrus.service;

import com.backend.eventsrus.dto.VendorSocialMediaLinkResponse;
import com.backend.eventsrus.enums.SocialMediaPlatform;
import com.backend.eventsrus.model.User;
import com.backend.eventsrus.model.VendorProfile;
import com.backend.eventsrus.model.VendorSocialMediaLink;
import com.backend.eventsrus.repository.UserRepository;
import com.backend.eventsrus.repository.VendorProfileRepository;
import com.backend.eventsrus.repository.VendorSocialMediaLinkRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A vendor's social media links (X, Instagram, Facebook, TikTok, YouTube) -
 * as many as they want, shown on the public storefront (see
 * VendorDirectoryService#getPublicProfile).
 */
@Service
@RequiredArgsConstructor
public class VendorSocialMediaLinkService {

    private final VendorSocialMediaLinkRepository vendorSocialMediaLinkRepository;
    private final VendorProfileRepository vendorProfileRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<VendorSocialMediaLinkResponse> listForVendor(String vendorEmail) {
        VendorProfile profile = requireProfile(vendorEmail);
        return vendorSocialMediaLinkRepository.findByVendorProfileIdOrderByCreatedAtAsc(profile.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public VendorSocialMediaLinkResponse addLink(String vendorEmail, SocialMediaPlatform platform, String url) {
        VendorProfile profile = requireProfile(vendorEmail);
        VendorSocialMediaLink saved = vendorSocialMediaLinkRepository.save(VendorSocialMediaLink.builder()
                .vendorProfile(profile)
                .platform(platform)
                .url(url.trim())
                .build());
        return toResponse(saved);
    }

    @Transactional
    public void deleteLink(String vendorEmail, Long linkId) {
        VendorProfile profile = requireProfile(vendorEmail);
        VendorSocialMediaLink link = vendorSocialMediaLinkRepository.findById(linkId)
                .orElseThrow(() -> new IllegalStateException("Social media link not found: " + linkId));
        if (!link.getVendorProfile().getId().equals(profile.getId())) {
            throw new IllegalStateException("Social media link does not belong to the authenticated vendor");
        }
        vendorSocialMediaLinkRepository.delete(link);
    }

    /**
     * One-time seed: the moment a vendor has a non-blank Facebook Page URL
     * and zero social media links of their own yet, turn that URL into
     * their first Facebook link. Called from UserService#becomeVendor (the
     * onboarding form, which is where the user explicitly asked this to
     * come from) and #updateSettings (the Business Info tab's copy of the
     * same field, for the same reason). Deliberately a one-time thing, not
     * an ongoing sync: once any social media link exists for a vendor
     * (seeded or vendor-added), editing this legacy field again never
     * touches the list - the Social Media tab is the independent source of
     * truth for the storefront from that point on.
     */
    @Transactional
    public void seedFacebookLinkIfNeeded(VendorProfile profile, String facebookPageUrl) {
        if (facebookPageUrl == null || facebookPageUrl.isBlank()) {
            return;
        }
        if (vendorSocialMediaLinkRepository.existsByVendorProfileId(profile.getId())) {
            return;
        }
        vendorSocialMediaLinkRepository.save(VendorSocialMediaLink.builder()
                .vendorProfile(profile)
                .platform(SocialMediaPlatform.FACEBOOK)
                .url(facebookPageUrl.trim())
                .build());
    }

    private VendorProfile requireProfile(String vendorEmail) {
        User user = userRepository.findByEmail(vendorEmail)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + vendorEmail));
        return vendorProfileRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalStateException("Vendor profile not found for user: " + user.getId()));
    }

    private VendorSocialMediaLinkResponse toResponse(VendorSocialMediaLink link) {
        return VendorSocialMediaLinkResponse.builder()
                .id(link.getId())
                .platform(link.getPlatform())
                .url(link.getUrl())
                .build();
    }
}
