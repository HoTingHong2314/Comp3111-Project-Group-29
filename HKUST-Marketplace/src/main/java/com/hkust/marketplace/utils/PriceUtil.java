package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingType;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Shared price validation and display helpers.
 * Prices accept at most 2 decimal places (e.g. 12, 12.3, 12.34).
 */
public final class PriceUtil {

    private PriceUtil() {
    }

    /**
     * Validates a price input string and returns {@code OK} or a user-facing error.
     */
    public static String validateInput(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return "Price is required.";
        }
        String value = raw.trim();
        if (!value.matches("\\d+(\\.\\d{1,2})?")) {
            return "Price must be a positive number with at most 2 decimal places.";
        }
        try {
            BigDecimal parsed = new BigDecimal(value);
            if (parsed.compareTo(BigDecimal.ZERO) <= 0) {
                return "Price must be greater than 0.";
            }
        } catch (NumberFormatException ex) {
            return "Price must be a valid number.";
        }
        return "OK";
    }

    /**
     * Parses a validated price input.
     */
    public static double parse(String raw) {
        String result = validateInput(raw);
        if (!"OK".equals(result)) {
            throw new IllegalArgumentException(result);
        }
        return new BigDecimal(raw.trim()).doubleValue();
    }

    /**
     * Returns whether a numeric amount is positive with at most two decimals.
     */
    public static boolean isValidAmount(double value) {
        if (value <= 0) {
            return false;
        }
        return BigDecimal.valueOf(value).stripTrailingZeros().scale() <= 2;
    }

    /**
     * Formats a price with exactly two decimal places.
     */
    public static String format(double value) {
        return BigDecimal.valueOf(value)
                .setScale(2, RoundingMode.HALF_UP)
                .toPlainString();
    }

    /**
     * Returns the effective price, treating non-sale listings as free.
     */
    public static double effectivePrice(Listing listing) {
        if (listing.getType() == ListingType.SELL && listing.getPrice() != null) {
            return listing.getPrice();
        }
        return 0.0; // Giveaways and lending listings are treated as free.
    }

    /**
     * Returns the display label for a listing price.
     */
    public static String displayListingPrice(Listing listing) {
        if (listing.getType() == ListingType.SELL && listing.getPrice() != null) {
            return "HK$ " + format(listing.getPrice());
        }
        if (listing.getType() == ListingType.LEND) {
            return "Borrow";
        }
        return "Free";
    }
}