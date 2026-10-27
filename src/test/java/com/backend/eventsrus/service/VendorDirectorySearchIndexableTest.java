package com.backend.eventsrus.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import com.backend.eventsrus.enums.PlanTier;
import com.backend.eventsrus.model.User;
import com.backend.eventsrus.model.VendorProfile;
import com.backend.eventsrus.repository.VendorProfileRepository;
import com.backend.eventsrus.repository.VendorPackageRepository;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** The rule for letting Google list a supplier's public page: opted in, complete, active plan. */
class VendorDirectorySearchIndexableTest {

    private VendorPlanService planService;
    private VendorDirectoryService service;
    private VendorProfile profile;

    @BeforeEach
    void setUp() {
        planService = mock(VendorPlanService.class);
        lenient().when(planService.getEffectivePlan(anyLong()))
                .thenReturn(new VendorPlanService.EffectivePlan(PlanTier.PRO, Instant.now().plusSeconds(86400), false, false, false, null, null));
        service = new VendorDirectoryService(
                mock(VendorProfileRepository.class), mock(VendorPackageRepository.class), null, null, null, null,
                null, null, null, null, planService);

        User user = new User();
        user.setId(7L);
        profile = new VendorProfile();
        profile.setUser(user);
        profile.setBusinessName("Sweet Moments Catering");
        profile.setDescription("Filipino buffet for weddings and debuts.");
        profile.setCity("Quezon City");
    }

    @Test
    void completeOptedInSupplierWithPlanIsIndexable() {
        assertThat(service.isSearchIndexable(profile, true)).isTrue();
    }

    @Test
    void supplierWhoOptedOutIsNeverIndexable() {
        profile.setSearchVisible(false);
        assertThat(service.isSearchIndexable(profile, true)).isFalse();
    }

    @Test
    void pageWithoutDescriptionCityOrContentStaysOut() {
        profile.setDescription("  ");
        assertThat(service.isSearchIndexable(profile, true)).isFalse();
        profile.setDescription("ok");
        profile.setCity(null);
        assertThat(service.isSearchIndexable(profile, true)).isFalse();
        profile.setCity("Cebu");
        assertThat(service.isSearchIndexable(profile, false)).isFalse();
    }

    @Test
    void lapsedPlanOrDeletedSupplierStaysOut() {
        Mockito.when(planService.getEffectivePlan(7L))
                .thenReturn(new VendorPlanService.EffectivePlan(null, null, false, false, false, null, null));
        assertThat(service.isSearchIndexable(profile, true)).isFalse();

        Mockito.when(planService.getEffectivePlan(7L))
                .thenReturn(new VendorPlanService.EffectivePlan(PlanTier.PRO, Instant.now().plusSeconds(60), false, false, false, null, null));
        profile.getUser().setDeletedAt(Instant.now());
        assertThat(service.isSearchIndexable(profile, true)).isFalse();
    }

    @Test
    void newProfilesAreOptedInByDefault() {
        assertThat(new VendorProfile().isSearchVisible()).isTrue();
    }
}
