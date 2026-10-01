package com.backend.eventsrus.service;

import com.backend.eventsrus.dto.VendorPackageGroupResponse;
import com.backend.eventsrus.exception.DuplicateTagException;
import com.backend.eventsrus.exception.InvalidTagNameException;
import com.backend.eventsrus.model.User;
import com.backend.eventsrus.model.VendorPackage;
import com.backend.eventsrus.model.VendorPackageGroup;
import com.backend.eventsrus.model.VendorProfile;
import com.backend.eventsrus.repository.UserRepository;
import com.backend.eventsrus.repository.VendorPackageGroupRepository;
import com.backend.eventsrus.repository.VendorPackageRepository;
import com.backend.eventsrus.repository.VendorProfileRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Vendor-defined groupings a vendor can attach to any of their packages -
 * same shape and conventions as VendorImageTagService (tags on images),
 * applied to packages instead. Reuses DuplicateTagException/
 * InvalidTagNameException rather than minting group-specific equivalents -
 * both are already generic "duplicate name"/"invalid name" errors, and the
 * message text at each throw site is what the vendor actually sees.
 */
@Service
@RequiredArgsConstructor
public class VendorPackageGroupService {

    // Letters (any script), digits, and single spaces only - same rule as
    // image tags, so a group name always reads as one clean word-or-phrase.
    private static final Pattern VALID_GROUP_NAME = Pattern.compile("^[\\p{L}\\p{N} ]+$");

    private final VendorPackageGroupRepository vendorPackageGroupRepository;
    private final VendorPackageRepository vendorPackageRepository;
    private final VendorProfileRepository vendorProfileRepository;
    private final UserRepository userRepository;

    @Transactional
    public VendorPackageGroupResponse createGroup(String vendorEmail, String name) {
        if (name == null || name.isBlank()) {
            throw new DuplicateTagException("A group name is required");
        }
        // Collapse any run of internal whitespace down to one space rather
        // than rejecting it - same reasoning as image tags.
        String normalized = name.trim().replaceAll("\\s+", " ");
        if (!VALID_GROUP_NAME.matcher(normalized).matches()) {
            throw new InvalidTagNameException("Group names can only contain letters, numbers, and spaces");
        }
        VendorProfile profile = requireProfile(vendorEmail);
        if (vendorPackageGroupRepository.findByVendorProfileIdAndNameIgnoreCase(profile.getId(), normalized).isPresent()) {
            throw new DuplicateTagException("You already have a group named \"" + normalized + "\"");
        }
        VendorPackageGroup saved = vendorPackageGroupRepository.save(VendorPackageGroup.builder()
                .vendorProfile(profile)
                .name(normalized)
                .build());
        return toGroupResponse(saved);
    }

    @Transactional
    public void deleteGroup(String vendorEmail, Long groupId) {
        VendorProfile profile = requireProfile(vendorEmail);
        VendorPackageGroup group = vendorPackageGroupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalStateException("Group not found: " + groupId));
        if (!group.getVendorProfile().getId().equals(profile.getId())) {
            throw new IllegalStateException("Group does not belong to the authenticated vendor");
        }
        vendorPackageGroupRepository.delete(group);
    }

    @Transactional(readOnly = true)
    public List<VendorPackageGroupResponse> listGroupsForVendor(String vendorEmail) {
        VendorProfile profile = requireProfile(vendorEmail);
        return vendorPackageGroupRepository.findByVendorProfileIdOrderByNameAsc(profile.getId()).stream()
                .map(this::toGroupResponse)
                .toList();
    }

    @Transactional
    public void setPackageGroups(String vendorEmail, Long packageId, List<Long> groupIds) {
        VendorProfile profile = requireProfile(vendorEmail);
        VendorPackage pkg = vendorPackageRepository.findById(packageId)
                .orElseThrow(() -> new IllegalStateException("Package not found: " + packageId));
        if (!pkg.getVendorProfile().getId().equals(profile.getId())) {
            throw new IllegalStateException("Package does not belong to the authenticated vendor");
        }
        pkg.setGroups(resolveOwnedGroups(profile.getId(), groupIds));
        vendorPackageRepository.save(pkg);
    }

    // Silently drops any id that isn't this vendor's own group - defends
    // against a tampered request submitting another vendor's group id.
    private Set<VendorPackageGroup> resolveOwnedGroups(Long vendorProfileId, List<Long> groupIds) {
        if (groupIds == null || groupIds.isEmpty()) {
            return new HashSet<>();
        }
        return new HashSet<>(vendorPackageGroupRepository.findByIdInAndVendorProfileId(groupIds, vendorProfileId));
    }

    private VendorProfile requireProfile(String vendorEmail) {
        User user = userRepository.findByEmail(vendorEmail)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + vendorEmail));
        return vendorProfileRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalStateException("Vendor profile not found for user: " + user.getId()));
    }

    private VendorPackageGroupResponse toGroupResponse(VendorPackageGroup group) {
        return VendorPackageGroupResponse.builder().id(group.getId()).name(group.getName()).build();
    }
}
