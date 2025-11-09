package com.voicemail.util;

import java.util.regex.Pattern;

/**
 * Utility class for validation operations
 */
public class ValidationUtil {

    // UDID patterns
    private static final Pattern UDID_HEX_PATTERN = Pattern.compile("^[0-9a-fA-F]{40}$");
    // Standard UUID format: 8-4-4-4-12 (e.g., 12345678-1234-1234-1234-123456789012)
    private static final Pattern UDID_UUID_PATTERN = Pattern.compile(
        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    );
    // Apple UUID format: 8-4-4-4-4 (e.g., 00008140-0001-688C-0213-001C)
    private static final Pattern UDID_APPLE_UUID_PATTERN = Pattern.compile("[0-9A-Fa-f]{8}-[0-9A-Fa-f]{16}");

    /**
     * Validate UDID format
     * @param udid UDID string to validate
     * @return true if valid UDID format (40 hex chars, standard UUID, or Apple UUID format)
     */
    public static boolean isValidUdid(String udid) {
        if (udid == null || udid.isEmpty()) {
            return false;
        }

        return UDID_HEX_PATTERN.matcher(udid).matches() ||
               UDID_UUID_PATTERN.matcher(udid).matches() ||
               UDID_APPLE_UUID_PATTERN.matcher(udid).matches();
    }
}
