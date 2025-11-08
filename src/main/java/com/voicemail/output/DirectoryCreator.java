package com.voicemail.output;

import com.voicemail.exception.PermissionException;
import com.voicemail.util.FileSystemUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Creates date-based directory structure for organizing voicemail files.
 * <p>
 * Directory format: {baseDir}/{YYYY-MM-DD}/
 * </p>
 */
public class DirectoryCreator {
    private static final Logger log = LoggerFactory.getLogger(DirectoryCreator.class);

    private static final DateTimeFormatter DIRECTORY_DATE_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * Create date-based subdirectory for a given timestamp.
     *
     * @param baseDir base output directory (e.g., "./voicemail-wavs")
     * @param timestamp voicemail received timestamp
     * @return path to date subdirectory (e.g., "./voicemail-wavs/2024-03-12")
     * @throws PermissionException if directory cannot be created due to permissions
     */
    public Path createDateDirectory(Path baseDir, Instant timestamp) throws PermissionException {
        Objects.requireNonNull(baseDir, "baseDir cannot be null");
        Objects.requireNonNull(timestamp, "timestamp cannot be null");

        String dateString = formatDateForDirectory(timestamp);
        Path dateDir = baseDir.resolve(dateString);

        try {
            if (!Files.exists(dateDir)) {
                Files.createDirectories(dateDir);
                log.info("Created date directory: {}", dateDir);
            } else {
                log.debug("Date directory already exists: {}", dateDir);
            }

            return dateDir;
        } catch (IOException e) {
            String message = String.format("Failed to create date directory: %s", dateDir);
            log.error(message, e);
            throw new PermissionException(dateDir, PermissionException.PermissionType.WRITE);
        }
    }

    /**
     * Ensure base output directories exist and are writable.
     *
     * @param wavOutputDir converted WAV files directory
     * @param backupDir original files directory (null if --keep-originals not set)
     * @throws PermissionException if directories cannot be created or are not writable
     */
    public void ensureBaseDirectoriesExist(Path wavOutputDir, Path backupDir)
            throws PermissionException {
        Objects.requireNonNull(wavOutputDir, "wavOutputDir cannot be null");

        // Create WAV output directory
        createAndValidateDirectory(wavOutputDir, "WAV output");

        // Create backup directory if specified
        if (backupDir != null) {
            createAndValidateDirectory(backupDir, "backup");
        }
    }

    /**
     * Check if sufficient disk space is available for output operations.
     *
     * @param outputDir directory to check
     * @param requiredBytes minimum required space in bytes
     * @return true if sufficient space available
     */
    public boolean hasSufficientSpace(Path outputDir, long requiredBytes) {
        Objects.requireNonNull(outputDir, "outputDir cannot be null");

        try {
            long availableBytes = FileSystemUtil.getAvailableSpace(outputDir);
            boolean sufficient = availableBytes >= requiredBytes;

            if (!sufficient) {
                log.warn("Insufficient disk space: {} required, {} available",
                    formatBytes(requiredBytes),
                    formatBytes(availableBytes));
            }

            return sufficient;
        } catch (IOException e) {
            log.warn("Failed to check disk space for {}: {}", outputDir, e.getMessage());
            return true;  // Assume sufficient if check fails
        }
    }

    // Private helper methods

    private String formatDateForDirectory(Instant timestamp) {
        return timestamp
            .atZone(ZoneId.systemDefault())
            .format(DIRECTORY_DATE_FORMATTER);
    }

    private void createAndValidateDirectory(Path directory, String description)
            throws PermissionException {
        try {
            // Create directory if it doesn't exist
            if (!Files.exists(directory)) {
                Files.createDirectories(directory);
                log.info("Created {} directory: {}", description, directory);
            }

            // Verify directory is writable
            if (!Files.isWritable(directory)) {
                String message = String.format("%s directory is not writable: %s",
                    description, directory);
                log.error(message);
                throw new PermissionException(directory, PermissionException.PermissionType.WRITE);
            }

            log.debug("{} directory verified: {}", description, directory);

        } catch (IOException e) {
            String message = String.format("Failed to create %s directory: %s",
                description, directory);
            log.error(message, e);
            throw new PermissionException(directory, PermissionException.PermissionType.WRITE);
        }
    }

    private String formatBytes(long bytes) {
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
