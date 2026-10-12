package com.backend.eventsrus.enums;

public enum ChecklistStatus {
    TODO,
    DONE,
    /** Crossed out - planner is no longer interested, but the item stays visible for reference (never deleted by this alone). */
    DISMISSED
}
