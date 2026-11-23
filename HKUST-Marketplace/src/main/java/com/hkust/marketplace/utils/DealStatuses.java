package com.hkust.marketplace.utils;

/**
 * Stable deal status values shared by the service and UI layers.
 */
public final class DealStatuses {

    public static final String PENDING = "Pending";
    public static final String COUNTERED = "Countered";
    public static final String ACCEPTED = "Accepted";
    public static final String DECLINED = "Declined";
    public static final String COMPLETED = "Completed";
    public static final String RETURNED = "Returned";
    public static final String CANCELLED = "Cancelled";

    private DealStatuses() {
    }
}
