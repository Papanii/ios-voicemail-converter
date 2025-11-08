package com.voicemail.output;

import com.voicemail.extractor.VoicemailFile;
import com.voicemail.metadata.PhoneNumberFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Generates safe, descriptive filenames for voicemail files based on metadata.
 * <p>
 * Filename format: {YYYYMMDD}-{HHMMSS}-{caller}.{extension}
 * </p>
 * <ul>
 *   <li>Timestamp from metadata (local timezone)</li>
 *   <li>Caller name or phone number (sanitized)</li>
 *   <li>Falls back to "Unknown_Caller" if no caller info</li>
 *   <li>Handles collisions with numeric suffix</li>
 * </ul>
 */
public class FilenameGenerator {
    private static final Logger log = LoggerFactory.getLogger(FilenameGenerator.class);

    private static final DateTimeFormatter DATE_FORMATTER =
        DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIME_FORMATTER =
        DateTimeFormatter.ofPattern("HHmmss");

    // Maximum filename length (excluding extension)
    private static final int MAX_FILENAME_LENGTH = 200;

    // Pattern for safe filename characters
    private static final Pattern UNSAFE_CHARS = Pattern.compile("[^a-zA-Z0-9_\\-+]");

    /**
     * Generate a filename for a WAV file.
     *
     * @param voicemailFile voicemail file with metadata
     * @return filename without collision suffix (e.g., "20240312-143022-John_Smith.wav")
     */
    public String generateWavFilename(VoicemailFile voicemailFile) {
        Objects.requireNonNull(voicemailFile, "voicemailFile cannot be null");

        if (!voicemailFile.hasMetadata()) {
            // Fall back to using original filename timestamp if available
            String filename = voicemailFile.getOriginalFilename();
            if (filename != null && !filename.isEmpty()) {
                // Try to extract timestamp from filename
                String baseFilename = filename.replaceFirst("\\.[^.]+$", "");
                return sanitizeForFilename(baseFilename) + ".wav";
            }
            return "Unknown_Voicemail.wav";
        }

        return generateFilename(voicemailFile.getMetadata(), "wav");
    }

    /**
     * Generate a filename for a JSON metadata file.
     *
     * @param voicemailFile voicemail file with metadata
     * @return filename without collision suffix (e.g., "20240312-143022-John_Smith.json")
     */
    public String generateJsonFilename(VoicemailFile voicemailFile) {
        Objects.requireNonNull(voicemailFile, "voicemailFile cannot be null");

        if (!voicemailFile.hasMetadata()) {
            String filename = voicemailFile.getOriginalFilename();
            if (filename != null && !filename.isEmpty()) {
                String baseFilename = filename.replaceFirst("\\.[^.]+$", "");
                return sanitizeForFilename(baseFilename) + ".json";
            }
            return "Unknown_Voicemail.json";
        }

        return generateFilename(voicemailFile.getMetadata(), "json");
    }

    /**
     * Generate a filename for the original audio file.
     *
     * @param voicemailFile voicemail file with metadata
     * @param originalExtension original file extension (e.g., "amr", "awb")
     * @return filename without collision suffix
     */
    public String generateOriginalFilename(VoicemailFile voicemailFile, String originalExtension) {
        Objects.requireNonNull(voicemailFile, "voicemailFile cannot be null");
        Objects.requireNonNull(originalExtension, "originalExtension cannot be null");

        // Remove leading dot if present
        String ext = originalExtension.startsWith(".") ?
            originalExtension.substring(1) : originalExtension;

        if (!voicemailFile.hasMetadata()) {
            String filename = voicemailFile.getOriginalFilename();
            if (filename != null && !filename.isEmpty()) {
                String baseFilename = filename.replaceFirst("\\.[^.]+$", "");
                return sanitizeForFilename(baseFilename) + "." + ext;
            }
            return "Unknown_Voicemail." + ext;
        }

        return generateFilename(voicemailFile.getMetadata(), ext);
    }

    /**
     * Generate a filename with collision handling.
     * <p>
     * If the base filename already exists, append numeric suffix: -1, -2, -3, etc.
     * </p>
     *
     * @param baseFilename base filename (e.g., "20240312-143022-John_Smith.wav")
     * @param existingNames set of existing filenames to check against
     * @return unique filename (e.g., "20240312-143022-John_Smith-2.wav")
     */
    public String generateUniqueFilename(String baseFilename, Set<String> existingNames) {
        Objects.requireNonNull(baseFilename, "baseFilename cannot be null");
        Objects.requireNonNull(existingNames, "existingNames cannot be null");

        if (!existingNames.contains(baseFilename)) {
            return baseFilename;
        }

        // Split into name and extension
        int dotIndex = baseFilename.lastIndexOf('.');
        String nameWithoutExt = dotIndex > 0 ? baseFilename.substring(0, dotIndex) : baseFilename;
        String extension = dotIndex > 0 ? baseFilename.substring(dotIndex) : "";

        // Try suffixes -1, -2, -3, ... up to 1000
        for (int i = 1; i <= 1000; i++) {
            String candidateFilename = nameWithoutExt + "-" + i + extension;
            if (!existingNames.contains(candidateFilename)) {
                log.debug("Resolved filename collision: {} -> {}", baseFilename, candidateFilename);
                return candidateFilename;
            }
        }

        // If we exhaust 1000 attempts, fall back to timestamp
        String fallback = nameWithoutExt + "-" + System.currentTimeMillis() + extension;
        log.warn("Could not resolve filename collision after 1000 attempts, using timestamp: {}",
            fallback);
        return fallback;
    }

    // Private helper methods

    private String generateFilename(VoicemailFile.VoicemailMetadata metadata, String extension) {
        Instant receivedDate = metadata.getReceivedDate();

        if (receivedDate == null) {
            // Fall back to current time if no received date
            receivedDate = Instant.now();
        }

        String datePart = formatDate(receivedDate);
        String timePart = formatTime(receivedDate);
        String callerPart = sanitizeCallerInfo(metadata);

        String filename = String.format("%s-%s-%s.%s", datePart, timePart, callerPart, extension);

        // Truncate if too long
        if (filename.length() > MAX_FILENAME_LENGTH) {
            int truncateLength = MAX_FILENAME_LENGTH - extension.length() - 1;
            filename = filename.substring(0, truncateLength) + "." + extension;
            log.debug("Truncated long filename to {} characters", MAX_FILENAME_LENGTH);
        }

        return filename;
    }

    private String formatDate(Instant timestamp) {
        return timestamp
            .atZone(ZoneId.systemDefault())
            .format(DATE_FORMATTER);
    }

    private String formatTime(Instant timestamp) {
        return timestamp
            .atZone(ZoneId.systemDefault())
            .format(TIME_FORMATTER);
    }

    private String sanitizeCallerInfo(VoicemailFile.VoicemailMetadata metadata) {
        String callerInfo = null;

        // Get caller number (we don't have caller name in our metadata)
        if (metadata.getCallerNumber() != null && !metadata.getCallerNumber().isEmpty()) {
            // Format the phone number for display
            callerInfo = PhoneNumberFormatter.formatForFilename(metadata.getCallerNumber());
        }

        // Last resort
        if (callerInfo == null || callerInfo.isEmpty() || "Unknown".equals(callerInfo)) {
            callerInfo = "Unknown_Caller";
        }

        // Replace unsafe characters with underscores
        String sanitized = UNSAFE_CHARS.matcher(callerInfo).replaceAll("_");

        // Remove leading/trailing underscores
        sanitized = sanitized.replaceAll("^_+|_+$", "");

        // Collapse multiple underscores
        sanitized = sanitized.replaceAll("_+", "_");

        // If sanitization resulted in empty string, use default
        if (sanitized.isEmpty()) {
            sanitized = "Unknown_Caller";
        }

        return sanitized;
    }

    private String sanitizeForFilename(String input) {
        if (input == null || input.isEmpty()) {
            return "Unknown";
        }

        // Replace unsafe characters with underscores
        String sanitized = UNSAFE_CHARS.matcher(input).replaceAll("_");

        // Remove leading/trailing underscores
        sanitized = sanitized.replaceAll("^_+|_+$", "");

        // Collapse multiple underscores
        sanitized = sanitized.replaceAll("_+", "_");

        // If empty after sanitization, use default
        if (sanitized.isEmpty()) {
            sanitized = "Unknown";
        }

        return sanitized;
    }
}
