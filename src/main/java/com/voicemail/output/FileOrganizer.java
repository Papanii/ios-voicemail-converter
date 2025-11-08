package com.voicemail.output;

import com.voicemail.exception.PermissionException;
import com.voicemail.extractor.VoicemailFile;
import com.voicemail.output.OutputResult.FileError;
import com.voicemail.output.OutputResult.OrganizedFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Orchestrates organization of converted voicemail files into output directories.
 * <p>
 * Responsibilities:
 * </p>
 * <ul>
 *   <li>Create date-based directory structure</li>
 *   <li>Generate descriptive filenames</li>
 *   <li>Copy converted WAV files to output directory</li>
 *   <li>Copy JSON metadata files to output directory</li>
 *   <li>Copy original files to backup directory (if --keep-originals)</li>
 *   <li>Handle filename collisions</li>
 *   <li>Track successes and failures</li>
 * </ul>
 */
public class FileOrganizer {
    private static final Logger log = LoggerFactory.getLogger(FileOrganizer.class);

    private final FilenameGenerator filenameGenerator;
    private final DirectoryCreator directoryCreator;
    private final OriginalFileKeeper originalFileKeeper;

    /**
     * File pairing for organization.
     * Pairs converted WAV file with its original source and metadata.
     */
    public static class FileToOrganize {
        private final Path wavFile;
        private final Path jsonFile;  // May be null
        private final Path originalFile;
        private final VoicemailFile voicemailFile;

        public FileToOrganize(Path wavFile, Path jsonFile, Path originalFile,
                             VoicemailFile voicemailFile) {
            this.wavFile = Objects.requireNonNull(wavFile, "wavFile cannot be null");
            this.jsonFile = jsonFile;  // Nullable
            this.originalFile = Objects.requireNonNull(originalFile, "originalFile cannot be null");
            this.voicemailFile = Objects.requireNonNull(voicemailFile, "voicemailFile cannot be null");
        }

        public Path getWavFile() { return wavFile; }
        public Path getJsonFile() { return jsonFile; }
        public Path getOriginalFile() { return originalFile; }
        public VoicemailFile getVoicemailFile() { return voicemailFile; }
    }

    public FileOrganizer() {
        this.filenameGenerator = new FilenameGenerator();
        this.directoryCreator = new DirectoryCreator();
        this.originalFileKeeper = new OriginalFileKeeper(filenameGenerator, directoryCreator);
    }

    // Constructor for testing with dependency injection
    FileOrganizer(FilenameGenerator filenameGenerator,
                  DirectoryCreator directoryCreator,
                  OriginalFileKeeper originalFileKeeper) {
        this.filenameGenerator = Objects.requireNonNull(filenameGenerator);
        this.directoryCreator = Objects.requireNonNull(directoryCreator);
        this.originalFileKeeper = Objects.requireNonNull(originalFileKeeper);
    }

    /**
     * Organize all converted voicemail files into output directories.
     *
     * @param filesToOrganize list of files to organize with their metadata
     * @param wavOutputDir base directory for WAV files (e.g., "./voicemail-wavs")
     * @param backupDir base directory for originals (null if --keep-originals not set)
     * @return result with statistics and error information
     * @throws PermissionException if output directories cannot be created
     */
    public OutputResult organizeFiles(List<FileToOrganize> filesToOrganize,
                                      Path wavOutputDir,
                                      Path backupDir) throws PermissionException {
        Objects.requireNonNull(filesToOrganize, "filesToOrganize cannot be null");
        Objects.requireNonNull(wavOutputDir, "wavOutputDir cannot be null");

        log.info("Organizing {} converted files to output directories",
            filesToOrganize.size());

        Instant startTime = Instant.now();

        // Ensure base directories exist
        directoryCreator.ensureBaseDirectoriesExist(wavOutputDir, backupDir);

        // Build result
        OutputResult.Builder resultBuilder = new OutputResult.Builder()
            .totalFiles(filesToOrganize.size());

        // Track existing filenames to handle collisions
        Set<String> existingWavFilenames = new HashSet<>();
        Set<String> existingBackupFilenames = new HashSet<>();

        int successCount = 0;
        int failureCount = 0;

        for (FileToOrganize fileToOrganize : filesToOrganize) {
            try {
                OrganizedFile organized = organizeFile(
                    fileToOrganize,
                    wavOutputDir,
                    backupDir,
                    existingWavFilenames,
                    existingBackupFilenames
                );

                resultBuilder.addOrganizedFile(organized);
                successCount++;

            } catch (Exception e) {
                log.error("Failed to organize file: {}",
                    fileToOrganize.getWavFile(), e);

                FileError error = new FileError(
                    fileToOrganize.getWavFile(),
                    e.getMessage(),
                    e
                );
                resultBuilder.addError(error);
                failureCount++;
            }
        }

        Duration duration = Duration.between(startTime, Instant.now());

        OutputResult result = resultBuilder
            .successfulFiles(successCount)
            .failedFiles(failureCount)
            .duration(duration)
            .build();

        log.info("File organization complete: {} succeeded, {} failed in {}",
            successCount, failureCount, duration);

        return result;
    }

    // Private helper methods

    private OrganizedFile organizeFile(FileToOrganize fileToOrganize,
                                       Path wavOutputDir,
                                       Path backupDir,
                                       Set<String> existingWavFilenames,
                                       Set<String> existingBackupFilenames)
            throws IOException, PermissionException {

        VoicemailFile voicemailFile = fileToOrganize.getVoicemailFile();
        Path wavFile = fileToOrganize.getWavFile();
        Path jsonFile = fileToOrganize.getJsonFile();
        Path originalFile = fileToOrganize.getOriginalFile();

        // Get timestamp for directory creation
        Instant receivedDate = voicemailFile.hasMetadata() ?
            voicemailFile.getMetadata().getReceivedDate() :
            Instant.now();

        // Create date subdirectory for WAV output
        Path wavDateDir = directoryCreator.createDateDirectory(wavOutputDir, receivedDate);

        // Generate base filename
        String baseWavFilename = filenameGenerator.generateWavFilename(voicemailFile);
        String uniqueWavFilename = filenameGenerator.generateUniqueFilename(
            baseWavFilename, existingWavFilenames);
        existingWavFilenames.add(uniqueWavFilename);

        // Copy WAV file
        Path wavDestination = wavDateDir.resolve(uniqueWavFilename);
        Files.copy(wavFile, wavDestination, StandardCopyOption.REPLACE_EXISTING);
        log.debug("Copied WAV file: {} -> {}", wavFile.getFileName(), wavDestination);

        // Copy JSON metadata if it exists
        Path jsonDestination = null;
        if (jsonFile != null && Files.exists(jsonFile)) {
            String jsonFilename = uniqueWavFilename.replace(".wav", ".json");
            jsonDestination = wavDateDir.resolve(jsonFilename);
            Files.copy(jsonFile, jsonDestination, StandardCopyOption.REPLACE_EXISTING);
            log.debug("Copied JSON file: {} -> {}", jsonFile.getFileName(), jsonDestination);
        } else {
            // Create dummy path for result
            jsonDestination = wavDestination.resolveSibling(
                uniqueWavFilename.replace(".wav", ".json"));
        }

        // Copy original file if --keep-originals is set
        Path originalDestination = null;
        if (originalFileKeeper.isKeepOriginalsEnabled(backupDir)) {
            String originalExtension = getFileExtension(originalFile);
            String baseOriginalFilename = filenameGenerator.generateOriginalFilename(
                voicemailFile, originalExtension);
            String uniqueOriginalFilename = filenameGenerator.generateUniqueFilename(
                baseOriginalFilename, existingBackupFilenames);
            existingBackupFilenames.add(uniqueOriginalFilename);

            Path backupDateDir = directoryCreator.createDateDirectory(backupDir, receivedDate);
            originalDestination = backupDateDir.resolve(uniqueOriginalFilename);
            Files.copy(originalFile, originalDestination, StandardCopyOption.REPLACE_EXISTING);
            log.debug("Copied original file: {} -> {}",
                originalFile.getFileName(), originalDestination);
        }

        // Get caller info for result
        String callerInfo = "Unknown";
        if (voicemailFile.hasMetadata() && voicemailFile.getMetadata().getCallerNumber() != null) {
            callerInfo = voicemailFile.getMetadata().getCallerNumber();
        }

        return new OrganizedFile(
            wavDestination,
            jsonDestination,
            originalDestination,
            callerInfo,
            receivedDate.toString()
        );
    }

    private String getFileExtension(Path file) {
        String filename = file.getFileName().toString();
        int dotIndex = filename.lastIndexOf('.');
        return (dotIndex > 0 && dotIndex < filename.length() - 1) ?
            filename.substring(dotIndex + 1) : "amr";
    }
}
