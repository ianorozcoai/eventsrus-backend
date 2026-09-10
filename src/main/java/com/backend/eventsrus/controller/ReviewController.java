package com.backend.eventsrus.controller;

import com.backend.eventsrus.dto.ReviewResponse;
import com.backend.eventsrus.dto.SubmitReviewRequest;
import com.backend.eventsrus.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    /** Planner leaves a first review on one of their completed bookings. */
    @PostMapping("/api/v1/bookings/{bookingId}/review")
    public ReviewResponse submit(
            @PathVariable Long bookingId, @Valid @RequestBody SubmitReviewRequest request, Authentication authentication) {
        return reviewService.submitReview(
                authentication.getName(), bookingId, request.getRating(), request.getComment());
    }

    /** Planner edits their own review. */
    @PutMapping("/api/v1/reviews/{reviewId}")
    public ReviewResponse update(
            @PathVariable Long reviewId, @Valid @RequestBody SubmitReviewRequest request, Authentication authentication) {
        return reviewService.updateReview(
                authentication.getName(), reviewId, request.getRating(), request.getComment());
    }
}
