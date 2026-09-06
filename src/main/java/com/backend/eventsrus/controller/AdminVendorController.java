package com.backend.eventsrus.controller;

import com.backend.eventsrus.dto.VendorVerificationDocumentsResponse;
import com.backend.eventsrus.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/vendors")
@RequiredArgsConstructor
public class AdminVendorController {

    private final UserService userService;

    @GetMapping("/{userId}/verification-documents")
    public VendorVerificationDocumentsResponse getVerificationDocuments(@PathVariable Long userId) {
        UserService.VendorVerificationDocuments documents = userService.getVerificationDocuments(userId);
        return VendorVerificationDocumentsResponse.builder()
                .idCardUrl(documents.idCardUrl())
                .selfieUrl(documents.selfieUrl())
                .legalDocuments(documents.legalDocuments())
                .build();
    }
}
