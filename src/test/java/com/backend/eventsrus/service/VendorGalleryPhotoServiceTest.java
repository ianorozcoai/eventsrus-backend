package com.backend.eventsrus.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.backend.eventsrus.enums.SystemSettingKey;
import com.backend.eventsrus.exception.InvalidFileTypeException;
import com.backend.eventsrus.model.User;
import com.backend.eventsrus.model.VendorGalleryPhoto;
import com.backend.eventsrus.model.VendorProfile;
import com.backend.eventsrus.repository.UserRepository;
import com.backend.eventsrus.repository.VendorGalleryPhotoRepository;
import com.backend.eventsrus.repository.VendorProfileRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

class VendorGalleryPhotoServiceTest {

    @ExtendWith(MockitoExtension.class)
    static class Base {
        @Mock
        VendorGalleryPhotoRepository vendorGalleryPhotoRepository;
        @Mock
        VendorProfileRepository vendorProfileRepository;
        @Mock
        UserRepository userRepository;
        @Mock
        S3UploadService s3UploadService;
        @Mock
        SystemSettingService systemSettingService;

        VendorGalleryPhotoService vendorGalleryPhotoService;

        static final User VENDOR = User.builder().id(1L).email("vendor@example.com").build();
        static final VendorProfile PROFILE = VendorProfile.builder().id(9L).user(VENDOR).build();

        @BeforeEach
        void setUp() {
            vendorGalleryPhotoService = new VendorGalleryPhotoService(
                    vendorGalleryPhotoRepository, vendorProfileRepository, userRepository, s3UploadService,
                    systemSettingService);
        }
    }

    @Nested
    class Create extends Base {

        @Test
        void uploadsAndSavesWhenUnderTheLimit() {
            when(userRepository.findByEmail("vendor@example.com")).thenReturn(Optional.of(VENDOR));
            when(vendorProfileRepository.findByUserId(1L)).thenReturn(Optional.of(PROFILE));
            when(systemSettingService.getInt(SystemSettingKey.VENDOR_GALLERY_PHOTO_LIMIT)).thenReturn(20);
            when(vendorGalleryPhotoRepository.countByVendorProfileId(9L)).thenReturn(5L);
            when(s3UploadService.upload(any(), anyString(), any()))
                    .thenReturn(new S3UploadService.UploadResult(null, "https://cdn.example.com/photo.jpg"));
            when(vendorGalleryPhotoRepository.save(any())).thenAnswer(invocation -> {
                VendorGalleryPhoto p = invocation.getArgument(0);
                p.setId(42L);
                return p;
            });

            MockMultipartFile image = new MockMultipartFile("image", "photo.jpg", "image/jpeg", "img".getBytes());
            var response = vendorGalleryPhotoService.create("vendor@example.com", image, "Sample work");

            assertThat(response.getId()).isEqualTo(42L);
            assertThat(response.getImageUrl()).isEqualTo("https://cdn.example.com/photo.jpg");
            assertThat(response.getCaption()).isEqualTo("Sample work");
        }

        @Test
        void rejectsUploadOnceAtTheLimit() {
            when(userRepository.findByEmail("vendor@example.com")).thenReturn(Optional.of(VENDOR));
            when(vendorProfileRepository.findByUserId(1L)).thenReturn(Optional.of(PROFILE));
            when(systemSettingService.getInt(SystemSettingKey.VENDOR_GALLERY_PHOTO_LIMIT)).thenReturn(20);
            when(vendorGalleryPhotoRepository.countByVendorProfileId(9L)).thenReturn(20L);

            MockMultipartFile image = new MockMultipartFile("image", "photo.jpg", "image/jpeg", "img".getBytes());

            assertThatThrownBy(() -> vendorGalleryPhotoService.create("vendor@example.com", image, null))
                    .isInstanceOf(InvalidFileTypeException.class)
                    .hasMessageContaining("20");
            verify(vendorGalleryPhotoRepository, never()).save(any());
        }

        @Test
        void rejectsNonImageContentType() {
            MockMultipartFile file = new MockMultipartFile("image", "doc.pdf", "application/pdf", "pdf".getBytes());

            assertThatThrownBy(() -> vendorGalleryPhotoService.create("vendor@example.com", file, null))
                    .isInstanceOf(InvalidFileTypeException.class);
            verify(vendorGalleryPhotoRepository, never()).save(any());
        }
    }

    @Nested
    class Delete extends Base {

        @Test
        void refusesToDeleteAnotherVendorsPhoto() {
            User otherVendor = User.builder().id(2L).email("other@example.com").build();
            VendorProfile otherProfile = VendorProfile.builder().id(99L).user(otherVendor).build();
            VendorGalleryPhoto photo = VendorGalleryPhoto.builder().id(5L).vendorProfile(otherProfile).imageUrl("x").build();

            when(userRepository.findByEmail("vendor@example.com")).thenReturn(Optional.of(VENDOR));
            when(vendorProfileRepository.findByUserId(1L)).thenReturn(Optional.of(PROFILE));
            when(vendorGalleryPhotoRepository.findById(5L)).thenReturn(Optional.of(photo));

            assertThatThrownBy(() -> vendorGalleryPhotoService.delete("vendor@example.com", 5L))
                    .isInstanceOf(IllegalStateException.class);
            verify(vendorGalleryPhotoRepository, never()).delete(any());
        }
    }
}
