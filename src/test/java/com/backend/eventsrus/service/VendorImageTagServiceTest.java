package com.backend.eventsrus.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.backend.eventsrus.enums.ImageSource;
import com.backend.eventsrus.exception.DuplicateTagException;
import com.backend.eventsrus.exception.InvalidTagNameException;
import com.backend.eventsrus.model.User;
import com.backend.eventsrus.model.VendorGalleryPhoto;
import com.backend.eventsrus.model.VendorImageTag;
import com.backend.eventsrus.model.VendorPackage;
import com.backend.eventsrus.model.VendorPackageImage;
import com.backend.eventsrus.model.VendorProfile;
import com.backend.eventsrus.repository.UserRepository;
import com.backend.eventsrus.repository.VendorGalleryPhotoRepository;
import com.backend.eventsrus.repository.VendorImageTagRepository;
import com.backend.eventsrus.repository.VendorPackageImageRepository;
import com.backend.eventsrus.repository.VendorProfileRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VendorImageTagServiceTest {

    @Mock
    private VendorImageTagRepository vendorImageTagRepository;
    @Mock
    private VendorGalleryPhotoRepository vendorGalleryPhotoRepository;
    @Mock
    private VendorPackageImageRepository vendorPackageImageRepository;
    @Mock
    private VendorProfileRepository vendorProfileRepository;
    @Mock
    private UserRepository userRepository;

    private VendorImageTagService vendorImageTagService;

    private static final User VENDOR = User.builder().id(1L).email("vendor@example.com").build();
    private static final VendorProfile PROFILE = VendorProfile.builder().id(9L).user(VENDOR).build();

    @BeforeEach
    void setUp() {
        vendorImageTagService = new VendorImageTagService(
                vendorImageTagRepository, vendorGalleryPhotoRepository, vendorPackageImageRepository,
                vendorProfileRepository, userRepository);
    }

    @Nested
    class CreateTag {

        @Test
        void rejectsADuplicateNameCaseInsensitively() {
            when(userRepository.findByEmail("vendor@example.com")).thenReturn(Optional.of(VENDOR));
            when(vendorProfileRepository.findByUserId(1L)).thenReturn(Optional.of(PROFILE));
            when(vendorImageTagRepository.findByVendorProfileIdAndNameIgnoreCase(9L, "Weddings"))
                    .thenReturn(Optional.of(VendorImageTag.builder().id(3L).vendorProfile(PROFILE).name("weddings").build()));

            assertThatThrownBy(() -> vendorImageTagService.createTag("vendor@example.com", "Weddings"))
                    .isInstanceOf(DuplicateTagException.class);
            verify(vendorImageTagRepository, never()).save(any());
        }

        @Test
        void rejectsABlankName() {
            assertThatThrownBy(() -> vendorImageTagService.createTag("vendor@example.com", "  "))
                    .isInstanceOf(DuplicateTagException.class);
            verify(vendorImageTagRepository, never()).save(any());
        }

        @Test
        void savesATrimmedTag() {
            when(userRepository.findByEmail("vendor@example.com")).thenReturn(Optional.of(VENDOR));
            when(vendorProfileRepository.findByUserId(1L)).thenReturn(Optional.of(PROFILE));
            when(vendorImageTagRepository.findByVendorProfileIdAndNameIgnoreCase(9L, "Outdoor")).thenReturn(Optional.empty());
            when(vendorImageTagRepository.save(any())).thenAnswer(inv -> {
                VendorImageTag t = inv.getArgument(0);
                t.setId(5L);
                return t;
            });

            var response = vendorImageTagService.createTag("vendor@example.com", "  Outdoor  ");

            assertThat(response.getId()).isEqualTo(5L);
            assertThat(response.getName()).isEqualTo("Outdoor");
        }

        @Test
        void collapsesRepeatedInternalSpacesBeforeSaving() {
            when(userRepository.findByEmail("vendor@example.com")).thenReturn(Optional.of(VENDOR));
            when(vendorProfileRepository.findByUserId(1L)).thenReturn(Optional.of(PROFILE));
            when(vendorImageTagRepository.findByVendorProfileIdAndNameIgnoreCase(9L, "Hair Style")).thenReturn(Optional.empty());
            when(vendorImageTagRepository.save(any())).thenAnswer(inv -> {
                VendorImageTag t = inv.getArgument(0);
                t.setId(5L);
                return t;
            });

            var response = vendorImageTagService.createTag("vendor@example.com", "Hair   Style");

            assertThat(response.getName()).isEqualTo("Hair Style");
        }

        @Test
        void treatsNamesDifferingOnlyByInternalSpacingAsDuplicates() {
            when(userRepository.findByEmail("vendor@example.com")).thenReturn(Optional.of(VENDOR));
            when(vendorProfileRepository.findByUserId(1L)).thenReturn(Optional.of(PROFILE));
            when(vendorImageTagRepository.findByVendorProfileIdAndNameIgnoreCase(9L, "Hair Style"))
                    .thenReturn(Optional.of(VendorImageTag.builder().id(3L).vendorProfile(PROFILE).name("Hair Style").build()));

            assertThatThrownBy(() -> vendorImageTagService.createTag("vendor@example.com", "Hair  Style"))
                    .isInstanceOf(DuplicateTagException.class);
            verify(vendorImageTagRepository, never()).save(any());
        }

        @Test
        void rejectsSpecialCharacters() {
            assertThatThrownBy(() -> vendorImageTagService.createTag("vendor@example.com", "Hair & Makeup!"))
                    .isInstanceOf(InvalidTagNameException.class);
            verify(vendorImageTagRepository, never()).save(any());
        }
    }

    @Nested
    class DeleteTag {

        @Test
        void refusesToDeleteAnotherVendorsTag() {
            User otherVendor = User.builder().id(2L).email("other@example.com").build();
            VendorProfile otherProfile = VendorProfile.builder().id(99L).user(otherVendor).build();
            VendorImageTag tag = VendorImageTag.builder().id(3L).vendorProfile(otherProfile).name("Outdoor").build();

            when(userRepository.findByEmail("vendor@example.com")).thenReturn(Optional.of(VENDOR));
            when(vendorProfileRepository.findByUserId(1L)).thenReturn(Optional.of(PROFILE));
            when(vendorImageTagRepository.findById(3L)).thenReturn(Optional.of(tag));

            assertThatThrownBy(() -> vendorImageTagService.deleteTag("vendor@example.com", 3L))
                    .isInstanceOf(IllegalStateException.class);
            verify(vendorImageTagRepository, never()).delete(any());
        }
    }

    @Nested
    class SetGalleryPhotoTags {

        @Test
        void refusesToTagAnotherVendorsPhoto() {
            User otherVendor = User.builder().id(2L).email("other@example.com").build();
            VendorProfile otherProfile = VendorProfile.builder().id(99L).user(otherVendor).build();
            VendorGalleryPhoto photo = VendorGalleryPhoto.builder().id(7L).vendorProfile(otherProfile).imageUrl("x").build();

            when(userRepository.findByEmail("vendor@example.com")).thenReturn(Optional.of(VENDOR));
            when(vendorProfileRepository.findByUserId(1L)).thenReturn(Optional.of(PROFILE));
            when(vendorGalleryPhotoRepository.findById(7L)).thenReturn(Optional.of(photo));

            assertThatThrownBy(() -> vendorImageTagService.setGalleryPhotoTags("vendor@example.com", 7L, List.of(1L)))
                    .isInstanceOf(IllegalStateException.class);
            verify(vendorGalleryPhotoRepository, never()).save(any());
        }

        // A tampered request could submit a tag id belonging to a different
        // vendor entirely - findByIdInAndVendorProfileId is the ownership
        // filter that silently drops it rather than letting it associate.
        @Test
        void silentlyDropsATagIdThatDoesNotBelongToThisVendor() {
            VendorGalleryPhoto photo = VendorGalleryPhoto.builder().id(7L).vendorProfile(PROFILE).imageUrl("x").build();
            VendorImageTag ownTag = VendorImageTag.builder().id(1L).vendorProfile(PROFILE).name("Outdoor").build();

            when(userRepository.findByEmail("vendor@example.com")).thenReturn(Optional.of(VENDOR));
            when(vendorProfileRepository.findByUserId(1L)).thenReturn(Optional.of(PROFILE));
            when(vendorGalleryPhotoRepository.findById(7L)).thenReturn(Optional.of(photo));
            // The repository itself is what enforces ownership - simulate it
            // returning only the tag that really belongs to this vendor,
            // even though the caller asked for two ids.
            when(vendorImageTagRepository.findByIdInAndVendorProfileId(List.of(1L, 999L), 9L))
                    .thenReturn(List.of(ownTag));

            vendorImageTagService.setGalleryPhotoTags("vendor@example.com", 7L, List.of(1L, 999L));

            ArgumentCaptor<VendorGalleryPhoto> captor = ArgumentCaptor.forClass(VendorGalleryPhoto.class);
            verify(vendorGalleryPhotoRepository).save(captor.capture());
            assertThat(captor.getValue().getTags()).containsExactly(ownTag);
        }
    }

    @Nested
    class SetPackageImageTags {

        @Test
        void refusesToTagAnImageFromAnotherVendorsPackage() {
            User otherVendor = User.builder().id(2L).email("other@example.com").build();
            VendorProfile otherProfile = VendorProfile.builder().id(99L).user(otherVendor).build();
            VendorPackage otherPackage = VendorPackage.builder().id(11L).vendorProfile(otherProfile).build();
            VendorPackageImage image = VendorPackageImage.builder().id(8L).vendorPackage(otherPackage).imageUrl("x").build();

            when(userRepository.findByEmail("vendor@example.com")).thenReturn(Optional.of(VENDOR));
            when(vendorProfileRepository.findByUserId(1L)).thenReturn(Optional.of(PROFILE));
            when(vendorPackageImageRepository.findById(8L)).thenReturn(Optional.of(image));

            assertThatThrownBy(() -> vendorImageTagService.setPackageImageTags("vendor@example.com", 8L, List.of(1L)))
                    .isInstanceOf(IllegalStateException.class);
            verify(vendorPackageImageRepository, never()).save(any());
        }
    }

    @Nested
    class ListAllTaggableImages {

        @Test
        void mergesGalleryPhotosAndPackageImagesSortedByCreatedAtDesc() {
            VendorPackage pkg = VendorPackage.builder().id(11L).vendorProfile(PROFILE).name("Premium").build();
            VendorGalleryPhoto photo = VendorGalleryPhoto.builder().id(7L).vendorProfile(PROFILE).imageUrl("gallery.jpg")
                    .createdAt(Instant.parse("2026-01-02T00:00:00Z")).build();
            VendorPackageImage image = VendorPackageImage.builder().id(8L).vendorPackage(pkg).imageUrl("package.jpg")
                    .createdAt(Instant.parse("2026-01-01T00:00:00Z")).build();

            when(userRepository.findByEmail("vendor@example.com")).thenReturn(Optional.of(VENDOR));
            when(vendorProfileRepository.findByUserId(1L)).thenReturn(Optional.of(PROFILE));
            when(vendorGalleryPhotoRepository.findByVendorProfileIdOrderByCreatedAtDesc(9L)).thenReturn(List.of(photo));
            when(vendorPackageImageRepository.findByVendorPackage_VendorProfile_IdAndVendorPackage_ActiveTrueOrderByCreatedAtDesc(9L))
                    .thenReturn(List.of(image));

            var result = vendorImageTagService.listAllTaggableImages("vendor@example.com");

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getSource()).isEqualTo(ImageSource.GALLERY);
            assertThat(result.get(1).getSource()).isEqualTo(ImageSource.PACKAGE);
            assertThat(result.get(1).getPackageName()).isEqualTo("Premium");
        }

        // A discontinued package's photos are invisible on the storefront -
        // the repository query itself filters them out (VendorPackage.active),
        // so this just documents that this service asks for the
        // active-only variant, not the unfiltered one.
        @Test
        void doesNotAskForImagesFromDiscontinuedPackages() {
            when(userRepository.findByEmail("vendor@example.com")).thenReturn(Optional.of(VENDOR));
            when(vendorProfileRepository.findByUserId(1L)).thenReturn(Optional.of(PROFILE));
            when(vendorGalleryPhotoRepository.findByVendorProfileIdOrderByCreatedAtDesc(9L)).thenReturn(List.of());
            when(vendorPackageImageRepository.findByVendorPackage_VendorProfile_IdAndVendorPackage_ActiveTrueOrderByCreatedAtDesc(9L))
                    .thenReturn(List.of());

            var result = vendorImageTagService.listAllTaggableImages("vendor@example.com");

            assertThat(result).isEmpty();
            verify(vendorPackageImageRepository, never()).findByVendorPackage_VendorProfile_IdOrderByCreatedAtDesc(any());
        }
    }
}
