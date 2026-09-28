package com.backend.eventsrus.controller;

import com.backend.eventsrus.dto.VendorImageTagResponse;
import com.backend.eventsrus.dto.VendorTaggedImageResponse;
import com.backend.eventsrus.service.VendorImageTagService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/vendors/me/image-tags")
@RequiredArgsConstructor
public class VendorImageTagController {

    private final VendorImageTagService vendorImageTagService;

    @PostMapping
    public VendorImageTagResponse create(@RequestBody Map<String, String> body, Authentication authentication) {
        return vendorImageTagService.createTag(authentication.getName(), body.get("name"));
    }

    @GetMapping
    public List<VendorImageTagResponse> list(Authentication authentication) {
        return vendorImageTagService.listTagsForVendor(authentication.getName());
    }

    @DeleteMapping("/{tagId}")
    public void delete(@PathVariable Long tagId, Authentication authentication) {
        vendorImageTagService.deleteTag(authentication.getName(), tagId);
    }

    /** Every image a vendor has (standalone gallery photos + every package's photos), for the Gallery Management page. */
    @GetMapping("/images")
    public List<VendorTaggedImageResponse> images(Authentication authentication) {
        return vendorImageTagService.listAllTaggableImages(authentication.getName());
    }
}
