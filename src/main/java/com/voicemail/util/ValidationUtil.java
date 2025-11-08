package com.voicemail.util;

import java.util.regex.Pattern;

/**
 * Utility class for validation operations
 */
public class ValidationUtil {

    // UDID patterns
    private static final Pattern UDID_HEX_PATTERN = Pattern.compile("^[0-9a-fA-F]{40}$");
    private static final Pattern UDID_UUID_PATTERN = Pattern.compile(
        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    );

    /**
     * Validate UDID format
     * @param udid UDID string to validate
     * @return true if valid UDID format (40 hex chars or UUID format)
     */
    public static boolean isValidUdid(String udid) {
        if (udid == null || udid.isEmpty()) {
            return false;
        }

        return UDID_HEX_PATTERN.matcher(udid).matches() ||
               UDID_UUID_PATTERN.matcher(udid).matches();
    }
}
