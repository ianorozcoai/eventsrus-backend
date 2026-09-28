package com.backend.eventsrus.enums;

/** Which underlying table a VendorTaggedImageResponse row actually came from - see VendorImageTagService#listAllTaggableImages. */
public enum ImageSource {
    GALLERY,
    PACKAGE
}
