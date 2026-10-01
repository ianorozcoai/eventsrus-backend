package com.backend.eventsrus.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.backend.eventsrus.enums.SocialMediaPlatform;
import com.backend.eventsrus.model.User;
import com.backend.eventsrus.model.VendorProfile;
import com.backend.eventsrus.model.VendorSocialMediaLink;
import com.backend.eventsrus.repository.UserRepository;
import com.backend.eventsrus.repository.VendorProfileRepository;
import com.backend.eventsrus.repository.VendorSocialMediaLinkRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VendorSocialMediaLinkServiceTest {

    @Mock
    private VendorSocialMediaLinkRepository vendorSocialMediaLinkRepository;
    @Mock
    private VendorProfileRepository vendorProfileRepository;
    @Mock
    private UserRepository userRepository;

    private VendorSocialMediaLinkService vendorSocialMediaLinkService;

    private static final User VENDOR = User.builder().id(1L).email("vendor@example.com").build();
    private static final VendorProfile PROFILE = VendorProfile.builder().id(9L).user(VENDOR).build();

    @BeforeEach
    void setUp() {
        vendorSocialMediaLinkService = new VendorSocialMediaLinkService(
                vendorSocialMediaLinkRepository, vendorProfileRepository, userRepository);
    }

    @Nested
    class AddLink {

        @Test
        void savesATrimmedUrl() {
            when(userRepository.findByEmail("vendor@example.com")).thenReturn(Optional.of(VENDOR));
            when(vendorProfileRepository.findByUserId(1L)).thenReturn(Optional.of(PROFILE));
            when(vendorSocialMediaLinkRepository.save(any())).thenAnswer(inv -> {
                VendorSocialMediaLink link = inv.getArgument(0);
                link.setId(5L);
                return link;
            });

            var response = vendorSocialMediaLinkService.addLink(
                    "vendor@example.com", SocialMediaPlatform.INSTAGRAM, "  https://instagram.com/test  ");

            assertThat(response.getId()).isEqualTo(5L);
            assertThat(response.getPlatform()).isEqualTo(SocialMediaPlatform.INSTAGRAM);
            assertThat(response.getUrl()).isEqualTo("https://instagram.com/test");
        }
    }

    @Nested
    class DeleteLink {

        @Test
        void refusesToDeleteAnotherVendorsLink() {
            User otherVendor = User.builder().id(2L).email("other@example.com").build();
            VendorProfile otherProfile = VendorProfile.builder().id(99L).user(otherVendor).build();
            VendorSocialMediaLink link = VendorSocialMediaLink.builder().id(3L).vendorProfile(otherProfile)
                    .platform(SocialMediaPlatform.TIKTOK).url("https://tiktok.com/@other").build();

            when(userRepository.findByEmail("vendor@example.com")).thenReturn(Optional.of(VENDOR));
            when(vendorProfileRepository.findByUserId(1L)).thenReturn(Optional.of(PROFILE));
            when(vendorSocialMediaLinkRepository.findById(3L)).thenReturn(Optional.of(link));

            assertThatThrownBy(() -> vendorSocialMediaLinkService.deleteLink("vendor@example.com", 3L))
                    .isInstanceOf(IllegalStateException.class);
            verify(vendorSocialMediaLinkRepository, never()).delete(any());
        }
    }

    @Nested
    class SeedFacebookLinkIfNeeded {

        @Test
        void doesNothingWhenTheUrlIsBlank() {
            vendorSocialMediaLinkService.seedFacebookLinkIfNeeded(PROFILE, "   ");

            verify(vendorSocialMediaLinkRepository, never()).existsByVendorProfileId(any());
            verify(vendorSocialMediaLinkRepository, never()).save(any());
        }

        @Test
        void doesNothingWhenTheVendorAlreadyHasAnyLink() {
            when(vendorSocialMediaLinkRepository.existsByVendorProfileId(9L)).thenReturn(true);

            vendorSocialMediaLinkService.seedFacebookLinkIfNeeded(PROFILE, "https://facebook.com/test");

            verify(vendorSocialMediaLinkRepository, never()).save(any());
        }

        @Test
        void seedsAFacebookLinkWhenNoneExistYet() {
            when(vendorSocialMediaLinkRepository.existsByVendorProfileId(9L)).thenReturn(false);

            vendorSocialMediaLinkService.seedFacebookLinkIfNeeded(PROFILE, "https://facebook.com/test");

            ArgumentCaptor<VendorSocialMediaLink> captor = ArgumentCaptor.forClass(VendorSocialMediaLink.class);
            verify(vendorSocialMediaLinkRepository).save(captor.capture());
            assertThat(captor.getValue().getPlatform()).isEqualTo(SocialMediaPlatform.FACEBOOK);
            assertThat(captor.getValue().getUrl()).isEqualTo("https://facebook.com/test");
            assertThat(captor.getValue().getVendorProfile()).isEqualTo(PROFILE);
        }
    }
}
