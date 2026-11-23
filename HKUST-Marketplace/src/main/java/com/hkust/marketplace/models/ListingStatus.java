package com.hkust.marketplace.models;

/**
 * Lifecycle state of a listing from review through reservation and completion.
 */
public enum ListingStatus {
    PENDING_REVIEW,
    LIVE,
    REJECTED,
    RESERVED,
    SOLD,
    LENT,
    GIVEN,
    REMOVED
}
