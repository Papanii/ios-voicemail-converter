package com.voicemail.util;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Utility for formatting strings (bytes, durations, timestamps)
 */
public class FormatUtil {

    private static final DateTimeFormatter FILENAME_TIMESTAMP =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss");

    private static final DateTimeFormatter DISPLAY_TIMESTAMP =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Format bytes in human-readable format
     */
    public static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024));
        } else {
            return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
        }
    }

    /**
     * Format duration in human-readable format
     */
    public static String formatDuration(Duration duration) {
        long seconds = duration.getSeconds();

        if (seconds < 60) {
            return seconds + "s";
        } else if (seconds < 3600) {
            long minutes = seconds / 60;
            long secs = seconds % 60;
            return String.format("%dm %ds", minutes, secs);
        } else {
            long hours = seconds / 3600;
            long minutes = (seconds % 3600) / 60;
            return String.format("%dh %dm", hours, minutes);
        }
    }

    /**
     * Format timestamp for filename (YYYY-MM-DDTHH-mm-ss)
     */
    public static String formatTimestampForFilename(Instant instant) {
        LocalDateTime ldt = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
        return ldt.format(FILENAME_TIMESTAMP);
    }

    /**
     * Format timestamp for display (YYYY-MM-DD HH:mm:ss)
     */
    public static String formatTimestampForDisplay(Instant instant) {
        LocalDateTime ldt = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
        return ldt.format(DISPLAY_TIMESTAMP);
    }

    /**
     * Format Unix timestamp for filename
     */
    public static String formatUnixTimestampForFilename(long unixTimestamp) {
        Instant instant = Instant.ofEpochSecond(unixTimestamp);
        return formatTimestampForFilename(instant);
    }

    /**
     * Format progress percentage
     */
    public static String formatProgress(int current, int total) {
        if (total == 0) {
            return "0%";
        }
        int percent = (int) ((current * 100.0) / total);
        return String.format("%d/%d (%d%%)", current, total, percent);
    }

    /**
     * Format progress bar
     */
    public static String formatProgressBar(int current, int total, int width) {
        if (total == 0) {
            return "[" + " ".repeat(width) + "]";
        }

        int filled = (int) ((current * width) / (double) total);
        int empty = width - filled;

        return "[" + "=".repeat(filled) + " ".repeat(empty) + "]";
    }
}
