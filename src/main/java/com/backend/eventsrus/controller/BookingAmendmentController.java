package com.backend.eventsrus.controller;

import com.backend.eventsrus.dto.BookingAmendmentRequest;
import com.backend.eventsrus.dto.BookingAmendmentResponse;
import com.backend.eventsrus.service.BookingAmendmentService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class BookingAmendmentController {

    private final BookingAmendmentService bookingAmendmentService;

    @PostMapping("/api/v1/bookings/{bookingId}/amendments")
    public BookingAmendmentResponse propose(
            @PathVariable Long bookingId, @Valid @RequestBody BookingAmendmentRequest request, Authentication authentication) {
        return bookingAmendmentService.propose(
                authentication.getName(), bookingId, request.getNewPrice(), request.getNewEventDatetime(),
                request.getNewAgreementDetails(), request.getNewPackageIds(), request.getNote());
    }

    @GetMapping("/api/v1/bookings/{bookingId}/amendments")
    public List<BookingAmendmentResponse> listForBooking(@PathVariable Long bookingId, Authentication authentication) {
        return bookingAmendmentService.listForBooking(authentication.getName(), bookingId);
    }

    @PutMapping("/api/v1/amendments/{amendmentId}/accept")
    public BookingAmendmentResponse accept(@PathVariable Long amendmentId, Authentication authentication) {
        return bookingAmendmentService.respond(authentication.getName(), amendmentId, true);
    }

    @PutMapping("/api/v1/amendments/{amendmentId}/reject")
    public BookingAmendmentResponse reject(@PathVariable Long amendmentId, Authentication authentication) {
        return bookingAmendmentService.respond(authentication.getName(), amendmentId, false);
    }

    @PutMapping("/api/v1/amendments/{amendmentId}/withdraw")
    public BookingAmendmentResponse withdraw(@PathVariable Long amendmentId, Authentication authentication) {
        return bookingAmendmentService.withdraw(authentication.getName(), amendmentId);
    }
}
