package com.backend.eventsrus.controller;

import com.backend.eventsrus.dto.VendorLegalDocumentRequest;
import com.backend.eventsrus.dto.VendorLegalDocumentResponse;
import com.backend.eventsrus.service.VendorLegalDocumentService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/vendors/me/legal-documents")
@RequiredArgsConstructor
public class VendorLegalDocumentController {

    private final VendorLegalDocumentService vendorLegalDocumentService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public VendorLegalDocumentResponse create(
            @Valid @ModelAttribute VendorLegalDocumentRequest request,
            @RequestPart MultipartFile file,
            Authentication authentication) {
        return vendorLegalDocumentService.create(
                authentication.getName(), request.getDocumentType(), request.getLabel(), file);
    }

    @GetMapping
    public List<VendorLegalDocumentResponse> list(Authentication authentication) {
        return vendorLegalDocumentService.listForVendor(authentication.getName());
    }

    @DeleteMapping("/{documentId}")
    public void delete(@PathVariable Long documentId, Authentication authentication) {
        vendorLegalDocumentService.delete(authentication.getName(), documentId);
    }
}
