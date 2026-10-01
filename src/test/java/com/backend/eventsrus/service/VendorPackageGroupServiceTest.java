package com.backend.eventsrus.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
class VendorPackageGroupServiceTest {

    @Mock
    private VendorPackageGroupRepository vendorPackageGroupRepository;
    @Mock
    private VendorPackageRepository vendorPackageRepository;
    @Mock
    private VendorProfileRepository vendorProfileRepository;
    @Mock
    private UserRepository userRepository;

    private VendorPackageGroupService vendorPackageGroupService;

    private static final User VENDOR = User.builder().id(1L).email("vendor@example.com").build();
    private static final VendorProfile PROFILE = VendorProfile.builder().id(9L).user(VENDOR).build();

    @BeforeEach
    void setUp() {
        vendorPackageGroupService = new VendorPackageGroupService(
                vendorPackageGroupRepository, vendorPackageRepository, vendorProfileRepository, userRepository);
    }

    @Nested
    class CreateGroup {

        @Test
        void rejectsADuplicateNameCaseInsensitively() {
            when(userRepository.findByEmail("vendor@example.com")).thenReturn(Optional.of(VENDOR));
            when(vendorProfileRepository.findByUserId(1L)).thenReturn(Optional.of(PROFILE));
            when(vendorPackageGroupRepository.findByVendorProfileIdAndNameIgnoreCase(9L, "Weddings"))
                    .thenReturn(Optional.of(VendorPackageGroup.builder().id(3L).vendorProfile(PROFILE).name("weddings").build()));

            assertThatThrownBy(() -> vendorPackageGroupService.createGroup("vendor@example.com", "Weddings"))
                    .isInstanceOf(DuplicateTagException.class);
            verify(vendorPackageGroupRepository, never()).save(any());
        }

        @Test
        void rejectsABlankName() {
            assertThatThrownBy(() -> vendorPackageGroupService.createGroup("vendor@example.com", "  "))
                    .isInstanceOf(DuplicateTagException.class);
            verify(vendorPackageGroupRepository, never()).save(any());
        }

        @Test
        void savesATrimmedGroup() {
            when(userRepository.findByEmail("vendor@example.com")).thenReturn(Optional.of(VENDOR));
            when(vendorProfileRepository.findByUserId(1L)).thenReturn(Optional.of(PROFILE));
            when(vendorPackageGroupRepository.findByVendorProfileIdAndNameIgnoreCase(9L, "Outdoor")).thenReturn(Optional.empty());
            when(vendorPackageGroupRepository.save(any())).thenAnswer(inv -> {
                VendorPackageGroup g = inv.getArgument(0);
                g.setId(5L);
                return g;
            });

            var response = vendorPackageGroupService.createGroup("vendor@example.com", "  Outdoor  ");

            assertThat(response.getId()).isEqualTo(5L);
            assertThat(response.getName()).isEqualTo("Outdoor");
        }

        @Test
        void collapsesRepeatedInternalSpacesBeforeSaving() {
            when(userRepository.findByEmail("vendor@example.com")).thenReturn(Optional.of(VENDOR));
            when(vendorProfileRepository.findByUserId(1L)).thenReturn(Optional.of(PROFILE));
            when(vendorPackageGroupRepository.findByVendorProfileIdAndNameIgnoreCase(9L, "Hair Style")).thenReturn(Optional.empty());
            when(vendorPackageGroupRepository.save(any())).thenAnswer(inv -> {
                VendorPackageGroup g = inv.getArgument(0);
                g.setId(5L);
                return g;
            });

            var response = vendorPackageGroupService.createGroup("vendor@example.com", "Hair   Style");

            assertThat(response.getName()).isEqualTo("Hair Style");
        }

        @Test
        void rejectsSpecialCharacters() {
            assertThatThrownBy(() -> vendorPackageGroupService.createGroup("vendor@example.com", "Weddings & More!"))
                    .isInstanceOf(InvalidTagNameException.class);
            verify(vendorPackageGroupRepository, never()).save(any());
        }
    }

    @Nested
    class DeleteGroup {

        @Test
        void refusesToDeleteAnotherVendorsGroup() {
            User otherVendor = User.builder().id(2L).email("other@example.com").build();
            VendorProfile otherProfile = VendorProfile.builder().id(99L).user(otherVendor).build();
            VendorPackageGroup group = VendorPackageGroup.builder().id(3L).vendorProfile(otherProfile).name("Outdoor").build();

            when(userRepository.findByEmail("vendor@example.com")).thenReturn(Optional.of(VENDOR));
            when(vendorProfileRepository.findByUserId(1L)).thenReturn(Optional.of(PROFILE));
            when(vendorPackageGroupRepository.findById(3L)).thenReturn(Optional.of(group));

            assertThatThrownBy(() -> vendorPackageGroupService.deleteGroup("vendor@example.com", 3L))
                    .isInstanceOf(IllegalStateException.class);
            verify(vendorPackageGroupRepository, never()).delete(any());
        }
    }

    @Nested
    class SetPackageGroups {

        @Test
        void refusesToGroupAnotherVendorsPackage() {
            User otherVendor = User.builder().id(2L).email("other@example.com").build();
            VendorProfile otherProfile = VendorProfile.builder().id(99L).user(otherVendor).build();
            VendorPackage otherPackage = VendorPackage.builder().id(11L).vendorProfile(otherProfile).build();

            when(userRepository.findByEmail("vendor@example.com")).thenReturn(Optional.of(VENDOR));
            when(vendorProfileRepository.findByUserId(1L)).thenReturn(Optional.of(PROFILE));
            when(vendorPackageRepository.findById(11L)).thenReturn(Optional.of(otherPackage));

            assertThatThrownBy(() -> vendorPackageGroupService.setPackageGroups("vendor@example.com", 11L, List.of(1L)))
                    .isInstanceOf(IllegalStateException.class);
            verify(vendorPackageRepository, never()).save(any());
        }

        // A tampered request could submit a group id belonging to a
        // different vendor entirely - findByIdInAndVendorProfileId is the
        // ownership filter that silently drops it rather than letting it
        // associate.
        @Test
        void silentlyDropsAGroupIdThatDoesNotBelongToThisVendor() {
            VendorPackage pkg = VendorPackage.builder().id(11L).vendorProfile(PROFILE).build();
            VendorPackageGroup ownGroup = VendorPackageGroup.builder().id(1L).vendorProfile(PROFILE).name("Outdoor").build();

            when(userRepository.findByEmail("vendor@example.com")).thenReturn(Optional.of(VENDOR));
            when(vendorProfileRepository.findByUserId(1L)).thenReturn(Optional.of(PROFILE));
            when(vendorPackageRepository.findById(11L)).thenReturn(Optional.of(pkg));
            when(vendorPackageGroupRepository.findByIdInAndVendorProfileId(List.of(1L, 999L), 9L))
                    .thenReturn(List.of(ownGroup));

            vendorPackageGroupService.setPackageGroups("vendor@example.com", 11L, List.of(1L, 999L));

            ArgumentCaptor<VendorPackage> captor = ArgumentCaptor.forClass(VendorPackage.class);
            verify(vendorPackageRepository).save(captor.capture());
            assertThat(captor.getValue().getGroups()).containsExactly(ownGroup);
        }
    }
}
