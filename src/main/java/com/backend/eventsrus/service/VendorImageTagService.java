package com.backend.eventsrus.service;

import com.backend.eventsrus.dto.VendorImageTagResponse;
import com.backend.eventsrus.dto.VendorTaggedImageResponse;
import com.backend.eventsrus.enums.ImageSource;
import com.backend.eventsrus.exception.DuplicateTagException;
import com.backend.eventsrus.exception.InvalidTagNameException;
import com.backend.eventsrus.model.User;
import com.backend.eventsrus.model.VendorGalleryPhoto;
import com.backend.eventsrus.model.VendorImageTag;
import com.backend.eventsrus.model.VendorPackageImage;
import com.backend.eventsrus.model.VendorProfile;
import com.backend.eventsrus.repository.UserRepository;
import com.backend.eventsrus.repository.VendorGalleryPhotoRepository;
import com.backend.eventsrus.repository.VendorImageTagRepository;
import com.backend.eventsrus.repository.VendorPackageImageRepository;
import com.backend.eventsrus.repository.VendorProfileRepository;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Vendor-defined labels a vendor can attach to any of their images -
 * standalone gallery photos (VendorGalleryPhoto) or package photos
 * (VendorPackageImage), the same two sources the storefront's Gallery
 * section already combines (see VendorDirectoryService#getPublicProfile).
 * Deliberately talks to VendorGalleryPhotoRepository/VendorPackageImageRepository
 * directly rather than through VendorGalleryPhotoService/VendorPackageImageService,
 * since tagging needs entity-level access (to mutate #tags), not those
 * services' DTO-returning methods.
 */
@Service
@RequiredArgsConstructor
public class VendorImageTagService {

    // Letters (any script), digits, and single spaces only - no punctuation
    // or symbols, so a tag name always reads as one clean word-or-phrase and
    // can't collide with itself over a stray "&"/"-" variant.
    private static final Pattern VALID_TAG_NAME = Pattern.compile("^[\\p{L}\\p{N} ]+$");

    private final VendorImageTagRepository vendorImageTagRepository;
    private final VendorGalleryPhotoRepository vendorGalleryPhotoRepository;
    private final VendorPackageImageRepository vendorPackageImageRepository;
    private final VendorProfileRepository vendorProfileRepository;
    private final UserRepository userRepository;

    @Transactional
    public VendorImageTagResponse createTag(String vendorEmail, String name) {
        if (name == null || name.isBlank()) {
            throw new DuplicateTagException("A tag name is required");
        }
        // Collapse any run of internal whitespace (double space, a stray tab)
        // down to one space rather than rejecting it - a vendor who
        // fat-fingers an extra space between words can't even see the
        // difference in the input box, so bouncing the submission back at
        // them would just be irritating for nothing they can visibly fix.
        String normalized = name.trim().replaceAll("\\s+", " ");
        if (!VALID_TAG_NAME.matcher(normalized).matches()) {
            throw new InvalidTagNameException("Tag names can only contain letters, numbers, and spaces");
        }
        VendorProfile profile = requireProfile(vendorEmail);
        // Duplicate check runs against the already-normalized name, so
        // "Hair Style" and "hair  style" (extra space, different case) are
        // correctly treated as the same tag.
        if (vendorImageTagRepository.findByVendorProfileIdAndNameIgnoreCase(profile.getId(), normalized).isPresent()) {
            throw new DuplicateTagException("You already have a tag named \"" + normalized + "\"");
        }
        VendorImageTag saved = vendorImageTagRepository.save(VendorImageTag.builder()
                .vendorProfile(profile)
                .name(normalized)
                .build());
        return toTagResponse(saved);
    }

    @Transactional
    public void deleteTag(String vendorEmail, Long tagId) {
        VendorProfile profile = requireProfile(vendorEmail);
        VendorImageTag tag = vendorImageTagRepository.findById(tagId)
                .orElseThrow(() -> new IllegalStateException("Tag not found: " + tagId));
        if (!tag.getVendorProfile().getId().equals(profile.getId())) {
            throw new IllegalStateException("Tag does not belong to the authenticated vendor");
        }
        vendorImageTagRepository.delete(tag);
    }

    @Transactional(readOnly = true)
    public List<VendorImageTagResponse> listTagsForVendor(String vendorEmail) {
        VendorProfile profile = requireProfile(vendorEmail);
        return vendorImageTagRepository.findByVendorProfileIdOrderByNameAsc(profile.getId()).stream()
                .map(this::toTagResponse)
                .toList();
    }

    /** Every image a vendor has - standalone gallery photos and every package's photos - combined for the Gallery Management page. */
    @Transactional(readOnly = true)
    public List<VendorTaggedImageResponse> listAllTaggableImages(String vendorEmail) {
        VendorProfile profile = requireProfile(vendorEmail);

        Stream<VendorTaggedImageResponse> galleryPhotos = vendorGalleryPhotoRepository
                .findByVendorProfileIdOrderByCreatedAtDesc(profile.getId()).stream()
                .map(this::toTaggedImageResponse);
        Stream<VendorTaggedImageResponse> packageImages = vendorPackageImageRepository
                .findByVendorPackage_VendorProfile_IdOrderByCreatedAtDesc(profile.getId()).stream()
                .map(this::toTaggedImageResponse);

        return Stream.concat(galleryPhotos, packageImages)
                .sorted(Comparator.comparing(VendorTaggedImageResponse::getCreatedAt, Comparator.reverseOrder()))
                .toList();
    }

    @Transactional
    public void setGalleryPhotoTags(String vendorEmail, Long photoId, List<Long> tagIds) {
        VendorProfile profile = requireProfile(vendorEmail);
        VendorGalleryPhoto photo = vendorGalleryPhotoRepository.findById(photoId)
                .orElseThrow(() -> new IllegalStateException("Gallery photo not found: " + photoId));
        if (!photo.getVendorProfile().getId().equals(profile.getId())) {
            throw new IllegalStateException("Gallery photo does not belong to the authenticated vendor");
        }
        photo.setTags(resolveOwnedTags(profile.getId(), tagIds));
        vendorGalleryPhotoRepository.save(photo);
    }

    @Transactional
    public void setPackageImageTags(String vendorEmail, Long imageId, List<Long> tagIds) {
        VendorProfile profile = requireProfile(vendorEmail);
        VendorPackageImage image = vendorPackageImageRepository.findById(imageId)
                .orElseThrow(() -> new IllegalStateException("Package image not found: " + imageId));
        if (!image.getVendorPackage().getVendorProfile().getId().equals(profile.getId())) {
            throw new IllegalStateException("Package image does not belong to the authenticated vendor");
        }
        image.setTags(resolveOwnedTags(profile.getId(), tagIds));
        vendorPackageImageRepository.save(image);
    }

    // Silently drops any id that isn't this vendor's own tag - defends
    // against a tampered request submitting another vendor's tag id.
    private Set<VendorImageTag> resolveOwnedTags(Long vendorProfileId, List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return new HashSet<>();
        }
        return new HashSet<>(vendorImageTagRepository.findByIdInAndVendorProfileId(tagIds, vendorProfileId));
    }

    private VendorProfile requireProfile(String vendorEmail) {
        User user = userRepository.findByEmail(vendorEmail)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + vendorEmail));
        return vendorProfileRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalStateException("Vendor profile not found for user: " + user.getId()));
    }

    private VendorImageTagResponse toTagResponse(VendorImageTag tag) {
        return VendorImageTagResponse.builder().id(tag.getId()).name(tag.getName()).build();
    }

    private VendorTaggedImageResponse toTaggedImageResponse(VendorGalleryPhoto photo) {
        return VendorTaggedImageResponse.builder()
                .id(photo.getId())
                .imageUrl(photo.getImageUrl())
                .caption(photo.getCaption())
                .createdAt(photo.getCreatedAt())
                .source(ImageSource.GALLERY)
                .tags(toTagResponses(photo.getTags()))
                .build();
    }

    private VendorTaggedImageResponse toTaggedImageResponse(VendorPackageImage image) {
        return VendorTaggedImageResponse.builder()
                .id(image.getId())
                .imageUrl(image.getImageUrl())
                .caption(image.getCaption())
                .createdAt(image.getCreatedAt())
                .source(ImageSource.PACKAGE)
                .packageId(image.getVendorPackage().getId())
                .packageName(image.getVendorPackage().getName())
                .tags(toTagResponses(image.getTags()))
                .build();
    }

    private List<VendorImageTagResponse> toTagResponses(Set<VendorImageTag> tags) {
        return tags.stream()
                .sorted(Comparator.comparing(VendorImageTag::getName))
                .map(this::toTagResponse)
                .toList();
    }
}
