package com.backend.eventsrus.controller;

import com.backend.eventsrus.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Review moderation - guarded by SecurityConfig's /api/v1/admin/** -> hasRole("ADMIN"). */
@RestController
@RequestMapping("/api/v1/admin/reviews")
@RequiredArgsConstructor
public class AdminReviewController {

    private final ReviewService reviewService;

    @PostMapping("/{reviewId}/hide")
    public void hide(@PathVariable Long reviewId) {
        reviewService.setHidden(reviewId, true);
    }

    @PostMapping("/{reviewId}/unhide")
    public void unhide(@PathVariable Long reviewId) {
        reviewService.setHidden(reviewId, false);
    }
}
