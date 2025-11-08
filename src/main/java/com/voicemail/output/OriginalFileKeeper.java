package com.voicemail.output;

import com.voicemail.exception.PermissionException;
import com.voicemail.extractor.VoicemailFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

/**
 * Handles preservation of original voicemail audio files.
 * <p>
 * When --keep-originals flag is set, copies original files (AMR, AWB, AAC)
 * to the backup directory with the same naming scheme as converted files.
 * </p>
 */
public class OriginalFileKeeper {
    private static final Logger log = LoggerFactory.getLogger(OriginalFileKeeper.class);

    private final FilenameGenerator filenameGenerator;
    private final DirectoryCreator directoryCreator;

    public OriginalFileKeeper(FilenameGenerator filenameGenerator,
                             DirectoryCreator directoryCreator) {
        this.filenameGenerator = Objects.requireNonNull(filenameGenerator,
            "filenameGenerator cannot be null");
        this.directoryCreator = Objects.requireNonNull(directoryCreator,
            "directoryCreator cannot be null");
    }

    /**
     * Copy original file to backup directory if --keep-originals is enabled.
     *
     * @param originalFile path to original audio file
     * @param voicemailFile voicemail file with metadata
     * @param backupDir base backup directory (e.g., "./voicemail-backup")
     * @return path to copied file, or null if backupDir is null
     * @throws IOException if copy operation fails
     * @throws PermissionException if directory creation fails
     */
    public Path copyOriginalFile(Path originalFile, VoicemailFile voicemailFile, Path backupDir)
            throws IOException, PermissionException {
        Objects.requireNonNull(originalFile, "originalFile cannot be null");
        Objects.requireNonNull(voicemailFile, "voicemailFile cannot be null");

        // If backup directory not specified, skip
        if (backupDir == null) {
            log.debug("Backup directory not specified, skipping original file copy");
            return null;
        }

        // Ensure original file exists
        if (!Files.exists(originalFile)) {
            log.warn("Original file does not exist, cannot copy: {}", originalFile);
            return null;
        }

        try {
            // Get received date from metadata, or use current time as fallback
            java.time.Instant receivedDate = voicemailFile.hasMetadata() ?
                voicemailFile.getMetadata().getReceivedDate() :
                java.time.Instant.now();

            // Create date subdirectory
            Path dateDir = directoryCreator.createDateDirectory(backupDir, receivedDate);

            // Get original file extension
            String originalExtension = getFileExtension(originalFile);

            // Generate filename
            String filename = filenameGenerator.generateOriginalFilename(
                voicemailFile, originalExtension);

            // Build destination path
            Path destinationPath = dateDir.resolve(filename);

            // Copy file
            Files.copy(originalFile, destinationPath, StandardCopyOption.REPLACE_EXISTING);

            log.info("Copied original file: {} -> {}",
                originalFile.getFileName(), destinationPath);

            return destinationPath;

        } catch (IOException e) {
            String message = String.format("Failed to copy original file %s to %s",
                originalFile, backupDir);
            log.error(message, e);
            throw new IOException(message, e);
        }
    }

    /**
     * Check if keeping originals is enabled.
     *
     * @param backupDir backup directory (null if --keep-originals not set)
     * @return true if originals should be kept
     */
    public boolean isKeepOriginalsEnabled(Path backupDir) {
        return backupDir != null;
    }

    // Private helper methods

    private String getFileExtension(Path file) {
        String filename = file.getFileName().toString();
        int dotIndex = filename.lastIndexOf('.');

        if (dotIndex > 0 && dotIndex < filename.length() - 1) {
            return filename.substring(dotIndex + 1);
        }

        // Default to "amr" if no extension found
        return "amr";
    }
}
