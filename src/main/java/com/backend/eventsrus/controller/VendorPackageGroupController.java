package com.backend.eventsrus.controller;

import com.backend.eventsrus.dto.VendorPackageGroupResponse;
import com.backend.eventsrus.service.VendorPackageGroupService;
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
@RequestMapping("/api/v1/vendors/me/package-groups")
@RequiredArgsConstructor
public class VendorPackageGroupController {

    private final VendorPackageGroupService vendorPackageGroupService;

    @PostMapping
    public VendorPackageGroupResponse create(@RequestBody Map<String, String> body, Authentication authentication) {
        return vendorPackageGroupService.createGroup(authentication.getName(), body.get("name"));
    }

    @GetMapping
    public List<VendorPackageGroupResponse> list(Authentication authentication) {
        return vendorPackageGroupService.listGroupsForVendor(authentication.getName());
    }

    @DeleteMapping("/{groupId}")
    public void delete(@PathVariable Long groupId, Authentication authentication) {
        vendorPackageGroupService.deleteGroup(authentication.getName(), groupId);
    }
}
