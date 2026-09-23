package com.backend.eventsrus.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.backend.eventsrus.enums.ReferralStatus;
import com.backend.eventsrus.model.User;
import com.backend.eventsrus.model.VendorReferral;
import com.backend.eventsrus.repository.UserRepository;
import com.backend.eventsrus.repository.VendorProfileRepository;
import com.backend.eventsrus.repository.VendorReferralRepository;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class VendorReferralServiceTest {

    @Mock
    private VendorReferralRepository vendorReferralRepository;
    @Mock
    private VendorProfileRepository vendorProfileRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private SystemSettingService systemSettingService;
    @Mock
    private S3UploadService s3UploadService;

    private VendorReferralService vendorReferralService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        vendorReferralService = new VendorReferralService(
                vendorReferralRepository, vendorProfileRepository, userRepository, systemSettingService, s3UploadService);
    }

    private VendorReferral convertedReferral() {
        return VendorReferral.builder()
                .referrer(User.builder().id(1L).email("referrer@example.com").build())
                .referred(User.builder().id(2L).email("referred@example.com").build())
                .status(ReferralStatus.CONVERTED)
                .build();
    }

    @Nested
    class MarkPaid {

        @Test
        void marksPaidWithNeitherRemarksNorProof() {
            VendorReferral referral = convertedReferral();
            when(vendorReferralRepository.findById(5L)).thenReturn(Optional.of(referral));
            when(vendorReferralRepository.save(any(VendorReferral.class))).thenAnswer(i -> i.getArgument(0));

            vendorReferralService.markPaid(5L, null, null);

            assertThat(referral.getStatus()).isEqualTo(ReferralStatus.COMMISSION_PAID);
            assertThat(referral.getPaidAt()).isNotNull();
            assertThat(referral.getPaymentRemarks()).isNull();
            assertThat(referral.getPaymentProofKey()).isNull();
            verify(s3UploadService, never()).upload(any(), any(), any());
        }

        @Test
        void marksPaidWithRemarksOnly() {
            VendorReferral referral = convertedReferral();
            when(vendorReferralRepository.findById(5L)).thenReturn(Optional.of(referral));
            when(vendorReferralRepository.save(any(VendorReferral.class))).thenAnswer(i -> i.getArgument(0));

            vendorReferralService.markPaid(5L, "Sent via GCash, ref #12345", null);

            assertThat(referral.getPaymentRemarks()).isEqualTo("Sent via GCash, ref #12345");
            assertThat(referral.getPaymentProofKey()).isNull();
        }

        @Test
        void uploadsProofWhenGiven() {
            VendorReferral referral = convertedReferral();
            when(vendorReferralRepository.findById(5L)).thenReturn(Optional.of(referral));
            when(vendorReferralRepository.save(any(VendorReferral.class))).thenAnswer(i -> i.getArgument(0));
            when(s3UploadService.upload(any(), any(), any()))
                    .thenReturn(new S3UploadService.UploadResult("referrals/5/payment-proof-abc.jpg", null));
            MockMultipartFile proof = new MockMultipartFile("proof", "deposit.jpg", "image/jpeg", new byte[]{1, 2, 3});

            vendorReferralService.markPaid(5L, null, proof);

            assertThat(referral.getPaymentProofKey()).isEqualTo("referrals/5/payment-proof-abc.jpg");
            assertThat(referral.getPaymentProofUploadedAt()).isNotNull();
        }

        @Test
        void throwsWhenReferralNotFound() {
            when(vendorReferralRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> vendorReferralService.markPaid(99L, null, null))
                    .isInstanceOf(IllegalStateException.class);
        }
    }
}
