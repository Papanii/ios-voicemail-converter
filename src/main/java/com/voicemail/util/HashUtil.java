package com.voicemail.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Utility for hashing operations (SHA-1 for iOS backup files)
 */
public class HashUtil {

    /**
     * Calculate SHA-1 hash of string
     */
    public static String sha1(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hashBytes = digest.digest(input.getBytes());
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-1 algorithm not available", e);
        }
    }

    /**
     * Calculate iOS backup file hash (domain + "-" + relativePath)
     */
    public static String calculateBackupFileHash(String domain, String relativePath) {
        String combined = domain + "-" + relativePath;
        return sha1(combined);
    }

    /**
     * Get backup file path from hash (first 2 chars as directory)
     */
    public static String getBackupFilePath(String hash) {
        if (hash.length() < 2) {
            throw new IllegalArgumentException("Hash too short: " + hash);
        }
        return hash.substring(0, 2) + "/" + hash;
    }

    /**
     * Convert byte array to hex string
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
