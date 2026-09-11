package com.backend.eventsrus.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.backend.eventsrus.enums.BusinessType;
import com.backend.eventsrus.enums.EventType;
import com.backend.eventsrus.enums.PlanTier;
import com.backend.eventsrus.model.User;
import com.backend.eventsrus.model.VendorProfile;
import com.backend.eventsrus.repository.VendorProfileRepository;
import com.backend.eventsrus.service.VendorPlanService.EffectivePlan;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * findMatchingVendors is the whole "recommended suppliers" pipeline the
 * planner Overview is built on - province/active-subscription/lead-time/
 * catered-event-type filters, plus the Top Vendor -> Verified sort. These
 * tests exercise each filter independently so a future change to one can't
 * silently break another.
 */
@ExtendWith(MockitoExtension.class)
class VendorSearchServiceTest {

    private static final String PROVINCE = "Metro Manila (NCR)";

    @Mock
    private VendorProfileRepository vendorProfileRepository;
    @Mock
    private VendorPlanService vendorPlanService;

    private VendorSearchService vendorSearchService;

    @BeforeEach
    void setUp() {
        vendorSearchService = new VendorSearchService(vendorProfileRepository, vendorPlanService);
    }

    private VendorProfile vendor(long userId, boolean verified, boolean topVendor, Integer leadTimeDays,
            List<EventType> cateredEventTypes) {
        User user = User.builder().id(userId).email("vendor" + userId + "@example.com").build();
        return VendorProfile.builder()
                .id(userId)
                .user(user)
                .businessType(BusinessType.CATERING)
                .verified(verified)
                .topVendor(topVendor)
                .leadTimeDays(leadTimeDays)
                .cateredEventTypes(cateredEventTypes)
                .build();
    }

    private void activeSubscriptionFor(long userId) {
        when(vendorPlanService.getEffectivePlan(userId)).thenReturn(new EffectivePlan(PlanTier.PRO, null, false, false));
    }

    private void noSubscriptionFor(long userId) {
        when(vendorPlanService.getEffectivePlan(userId)).thenReturn(new EffectivePlan(null, null, false, false));
    }

    @Test
    void returnsEmptyWhenProvinceIsBlank() {
        assertThat(vendorSearchService.findMatchingVendors(BusinessType.CATERING, "  ", null, null)).isEmpty();
        assertThat(vendorSearchService.findMatchingVendors(BusinessType.CATERING, null, null, null)).isEmpty();
    }

    @Test
    void excludesVendorsWithNoActiveSubscription() {
        VendorProfile active = vendor(1L, false, false, null, List.of());
        VendorProfile lapsed = vendor(2L, false, false, null, List.of());
        when(vendorProfileRepository.findByBusinessTypeAndOperatingArea(BusinessType.CATERING, PROVINCE))
                .thenReturn(List.of(active, lapsed));
        activeSubscriptionFor(1L);
        noSubscriptionFor(2L);

        var result = vendorSearchService.findMatchingVendors(BusinessType.CATERING, PROVINCE, null, null);

        assertThat(result).extracting(vp -> vp.getUser().getId()).containsExactly(1L);
    }

    @Nested
    class LeadTime {

        @Test
        void excludesVendorWhoseLeadTimeMakesTheDateTooSoon() {
            VendorProfile needsTwoWeeks = vendor(1L, false, false, 14, List.of());
            when(vendorProfileRepository.findByBusinessTypeAndOperatingArea(BusinessType.CATERING, PROVINCE))
                    .thenReturn(List.of(needsTwoWeeks));
            activeSubscriptionFor(1L);

            var tomorrow = LocalDate.now().plusDays(1);
            var result = vendorSearchService.findMatchingVendors(BusinessType.CATERING, PROVINCE, tomorrow, null);

            assertThat(result).isEmpty();
        }

        @Test
        void includesVendorWhenEventIsFarEnoughOut() {
            VendorProfile needsTwoWeeks = vendor(1L, false, false, 14, List.of());
            when(vendorProfileRepository.findByBusinessTypeAndOperatingArea(BusinessType.CATERING, PROVINCE))
                    .thenReturn(List.of(needsTwoWeeks));
            activeSubscriptionFor(1L);

            var farOut = LocalDate.now().plusDays(30);
            var result = vendorSearchService.findMatchingVendors(BusinessType.CATERING, PROVINCE, farOut, null);

            assertThat(result).hasSize(1);
        }

        @Test
        void noLeadTimeDeclaredNeverExcludesOnDate() {
            VendorProfile noLeadTime = vendor(1L, false, false, null, List.of());
            when(vendorProfileRepository.findByBusinessTypeAndOperatingArea(BusinessType.CATERING, PROVINCE))
                    .thenReturn(List.of(noLeadTime));
            activeSubscriptionFor(1L);

            var result = vendorSearchService.findMatchingVendors(
                    BusinessType.CATERING, PROVINCE, LocalDate.now(), null);

            assertThat(result).hasSize(1);
        }
    }

    @Nested
    class CateredEventTypeFilter {

        @Test
        void excludesVendorWhoDeclaredTypesThatDontIncludeThisEvent() {
            VendorProfile weddingOnly = vendor(1L, false, false, null, List.of(EventType.WEDDING));
            when(vendorProfileRepository.findByBusinessTypeAndOperatingArea(BusinessType.CATERING, PROVINCE))
                    .thenReturn(List.of(weddingOnly));
            activeSubscriptionFor(1L);

            var result = vendorSearchService.findMatchingVendors(
                    BusinessType.CATERING, PROVINCE, null, EventType.CORPORATE_EVENT);

            assertThat(result).isEmpty();
        }

        @Test
        void includesVendorWhoDeclaredThisEventType() {
            VendorProfile weddingCaterer = vendor(1L, false, false, null, List.of(EventType.WEDDING, EventType.DEBUT));
            when(vendorProfileRepository.findByBusinessTypeAndOperatingArea(BusinessType.CATERING, PROVINCE))
                    .thenReturn(List.of(weddingCaterer));
            activeSubscriptionFor(1L);

            var result = vendorSearchService.findMatchingVendors(BusinessType.CATERING, PROVINCE, null, EventType.DEBUT);

            assertThat(result).hasSize(1);
        }

        @Test
        void vendorWithNoDeclaredTypesIsNeverExcludedByEventType() {
            VendorProfile undeclared = vendor(1L, false, false, null, List.of());
            when(vendorProfileRepository.findByBusinessTypeAndOperatingArea(BusinessType.CATERING, PROVINCE))
                    .thenReturn(List.of(undeclared));
            activeSubscriptionFor(1L);

            var result = vendorSearchService.findMatchingVendors(BusinessType.CATERING, PROVINCE, null, EventType.GALA);

            assertThat(result).hasSize(1);
        }
    }

    @Test
    void sortsTopVendorsFirstThenVerified() {
        VendorProfile plain = vendor(1L, false, false, null, List.of());
        VendorProfile verifiedOnly = vendor(2L, true, false, null, List.of());
        VendorProfile topVendor = vendor(3L, false, true, null, List.of());
        when(vendorProfileRepository.findByBusinessTypeAndOperatingArea(BusinessType.CATERING, PROVINCE))
                .thenReturn(List.of(plain, verifiedOnly, topVendor));
        activeSubscriptionFor(1L);
        activeSubscriptionFor(2L);
        activeSubscriptionFor(3L);

        var result = vendorSearchService.findMatchingVendors(BusinessType.CATERING, PROVINCE, null, null);

        assertThat(result).extracting(vp -> vp.getUser().getId()).containsExactly(3L, 2L, 1L);
    }
}
