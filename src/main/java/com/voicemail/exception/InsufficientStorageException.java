package com.voicemail.exception;

import java.nio.file.Path;

/**
 * Exception thrown when insufficient disk space is available.
 * Exit code: 8
 */
public class InsufficientStorageException extends VoicemailConverterException {
    private final Path location;
    private final long requiredBytes;
    private final long availableBytes;

    public InsufficientStorageException(Path location, long requiredBytes, long availableBytes) {
        super(
            buildMessage(location, requiredBytes, availableBytes),
            8,
            buildSuggestion(location, requiredBytes, availableBytes)
        );
        this.location = location;
        this.requiredBytes = requiredBytes;
        this.availableBytes = availableBytes;
    }

    public Path getLocation() {
        return location;
    }

    public long getRequiredBytes() {
        return requiredBytes;
    }

    public long getAvailableBytes() {
        return availableBytes;
    }

    private static String buildMessage(Path location, long required, long available) {
        return String.format(
            "Insufficient disk space at %s: Need %s, have %s",
            location,
            formatBytes(required),
            formatBytes(available)
        );
    }

    private static String buildSuggestion(Path location, long required, long available) {
        long shortage = required - available;
        return String.format(
            "Free up at least %s of disk space, or:\n" +
            "  - Use a different output directory with --output-dir\n" +
            "  - Choose a location with more available space",
            formatBytes(shortage)
        );
    }

    private static String formatBytes(long bytes) {
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
}
