package com.hkust.marketplace.utils;

import org.mindrot.jbcrypt.BCrypt;

/**
 * Password hashing and validation helpers.
 */
public final class PasswordUtil {

    private PasswordUtil() {
    }

    /**
     * Hashes a raw password with BCrypt.
     */
    public static String hash(String raw) {
        return BCrypt.hashpw(raw, BCrypt.gensalt(12));
    }

    /**
     * Returns whether a stored value looks like a BCrypt hash.
     */
    public static boolean isHashed(String value) {
        return value != null
                && (value.startsWith("$2a$") || value.startsWith("$2b$") || value.startsWith("$2y$"));
    }

    /**
     * Verifies a raw password against a stored BCrypt hash.
     */
    public static boolean verify(String raw, String stored) {
        if (raw == null || stored == null) {
            return false;
        }
        if (!isHashed(stored)) {
            return false;
        }
        try {
            return BCrypt.checkpw(raw, stored);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    /**
     * Validates password strength and returns {@code OK} when accepted.
     */
    public static String validate(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "Password cannot be empty.";
        }
        if (raw.length() < 8) {
            return "Password must be at least 8 characters.";
        }
        boolean hasUpper = raw.chars().anyMatch(Character::isUpperCase);
        boolean hasLower = raw.chars().anyMatch(Character::isLowerCase);
        boolean hasDigit = raw.chars().anyMatch(Character::isDigit);
        if (!hasUpper || !hasLower || !hasDigit) {
            return "Password should contain uppercase, lowercase and a digit.";
        }
        return "OK";
    }
}
