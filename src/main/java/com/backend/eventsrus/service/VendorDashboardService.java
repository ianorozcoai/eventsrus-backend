package com.backend.eventsrus.service;

import com.backend.eventsrus.dto.VendorDashboardResponse;
import com.backend.eventsrus.enums.BookingStatus;
import com.backend.eventsrus.enums.QuotationStatus;
import com.backend.eventsrus.model.Booking;
import com.backend.eventsrus.model.Conversation;
import com.backend.eventsrus.model.User;
import com.backend.eventsrus.repository.BookingRepository;
import com.backend.eventsrus.repository.ConversationMessageRepository;
import com.backend.eventsrus.repository.ConversationRepository;
import com.backend.eventsrus.repository.LeadRepository;
import com.backend.eventsrus.repository.QuotationRepository;
import com.backend.eventsrus.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VendorDashboardService {

    private final UserRepository userRepository;
    private final LeadRepository leadRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationMessageRepository conversationMessageRepository;
    private final QuotationRepository quotationRepository;
    private final BookingRepository bookingRepository;
    private final VendorPackageService vendorPackageService;

    @Transactional(readOnly = true)
    public VendorDashboardResponse getDashboard(String vendorEmail) {
        User vendor = userRepository.findByEmail(vendorEmail)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + vendorEmail));
        Instant sevenDaysAgo = Instant.now().minus(7, ChronoUnit.DAYS);

        long newLeads = leadRepository.findByVendorUserIdOrderByLastVisitedAtDesc(vendor.getId()).stream()
                .filter(l -> l.getFirstVisitedAt().isAfter(sevenDaysAgo))
                .count();

        long unreadMessages = 0;
        for (Conversation conversation : conversationRepository.findByVendorUserIdOrderByUpdatedAtDesc(vendor.getId())) {
            unreadMessages += conversationMessageRepository
                    .countByConversationIdAndReadAtIsNullAndSenderIdNot(conversation.getId(), vendor.getId());
        }

        long newQuotations = quotationRepository.findByVendorUserIdOrderByCreatedAtDesc(vendor.getId()).stream()
                .filter(q -> q.getStatus() == QuotationStatus.REQUESTED)
                .count();

        var bookings = bookingRepository.findByVendorUserIdOrderByEventDatetimeAsc(vendor.getId());
        long newBookings = bookings.stream()
                .filter(b -> b.getStatus() == BookingStatus.APPROVED
                        && b.getRespondedAt() != null && b.getRespondedAt().isAfter(sevenDaysAgo))
                .count();
        long upcomingEvents = bookings.stream()
                .filter(b -> b.getStatus() == BookingStatus.APPROVED
                        && b.getEventDatetime() != null && b.getEventDatetime().isAfter(Instant.now()))
                .count();
        BigDecimal totalIncome = bookings.stream()
                .filter(b -> b.getStatus() == BookingStatus.APPROVED && b.getPrice() != null)
                .map(Booking::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long cancellations = bookings.stream().filter(b -> b.getStatus() == BookingStatus.DECLINED).count();

        return VendorDashboardResponse.builder()
                .newLeadsCount(newLeads)
                .newMessagesCount(unreadMessages)
                .newQuotationsCount(newQuotations)
                .newBookingsCount(newBookings)
                .upcomingEventsCount(upcomingEvents)
                .totalIncome(totalIncome)
                .cancellationsCount(cancellations)
                .hasPackages(vendorPackageService.hasAnyPackages(vendorEmail))
                .build();
    }
}
