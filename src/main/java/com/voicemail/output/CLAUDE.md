# Output Module - Implementation Guide

**Module:** `com.voicemail.output`
**Purpose:** Organize and save converted voicemail files to output directories
**Status:** 📋 Specification Complete - Ready for Implementation
**Dependencies:** Converter, Metadata, Exception, Util

---

## Overview

The Output module is responsible for organizing converted voicemail files and their metadata into a structured directory layout. It handles:

1. **Filename Generation** - Creating descriptive filenames from metadata
2. **Directory Structure** - Creating date-based folder hierarchy
3. **File Organization** - Copying/moving files to correct locations
4. **Original File Preservation** - Managing `--keep-originals` flag behavior
5. **JSON Export** - Saving metadata sidecar files

---

## Module Architecture

```
com.voicemail.output/
├── FilenameGenerator.java      (Generate safe, descriptive filenames)
├── DirectoryCreator.java        (Create date-based directory structure)
├── FileOrganizer.java           (Main orchestrator)
├── OriginalFileKeeper.java      (Handle original file copying)
└── OutputResult.java            (Result data class)
```

---

## Class Specifications

### 1. OutputResult (Data Class)

**Purpose:** Immutable result object for file organization operations

**File:** `OutputResult.java`

```java
package com.voicemail.output;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Result of organizing converted voicemail files to output directories.
 * <p>
 * This immutable class contains information about successfully organized files,
 * failures, and overall statistics.
 * </p>
 */
public class OutputResult {
    private final int totalFiles;
    private final int successfulFiles;
    private final int failedFiles;
    private final List<OrganizedFile> organizedFiles;
    private final List<FileError> errors;
    private final Duration duration;

    /**
     * Information about a successfully organized file.
     */
    public static class OrganizedFile {
        private final Path wavFile;
        private final Path jsonFile;
        private final Path originalFile;  // null if --keep-originals not used
        private final String callerInfo;
        private final String receivedDate;

        public OrganizedFile(Path wavFile, Path jsonFile, Path originalFile,
                           String callerInfo, String receivedDate) {
            this.wavFile = Objects.requireNonNull(wavFile, "wavFile cannot be null");
            this.jsonFile = Objects.requireNonNull(jsonFile, "jsonFile cannot be null");
            this.originalFile = originalFile;  // Nullable
            this.callerInfo = callerInfo;
            this.receivedDate = receivedDate;
        }

        public Path getWavFile() { return wavFile; }
        public Path getJsonFile() { return jsonFile; }
        public Path getOriginalFile() { return originalFile; }
        public String getCallerInfo() { return callerInfo; }
        public String getReceivedDate() { return receivedDate; }

        @Override
        public String toString() {
            return String.format("OrganizedFile[wav=%s, json=%s, original=%s, caller=%s]",
                wavFile.getFileName(), jsonFile.getFileName(),
                originalFile != null ? originalFile.getFileName() : "none",
                callerInfo);
        }
    }

    /**
     * Information about a file that failed to organize.
     */
    public static class FileError {
        private final Path sourceFile;
        private final String errorMessage;
        private final Exception exception;

        public FileError(Path sourceFile, String errorMessage, Exception exception) {
            this.sourceFile = Objects.requireNonNull(sourceFile, "sourceFile cannot be null");
            this.errorMessage = Objects.requireNonNull(errorMessage, "errorMessage cannot be null");
            this.exception = exception;  // Nullable
        }

        public Path getSourceFile() { return sourceFile; }
        public String getErrorMessage() { return errorMessage; }
        public Exception getException() { return exception; }

        @Override
        public String toString() {
            return String.format("FileError[file=%s, error=%s]",
                sourceFile.getFileName(), errorMessage);
        }
    }

    private OutputResult(Builder builder) {
        this.totalFiles = builder.totalFiles;
        this.successfulFiles = builder.successfulFiles;
        this.failedFiles = builder.failedFiles;
        this.organizedFiles = Collections.unmodifiableList(builder.organizedFiles);
        this.errors = Collections.unmodifiableList(builder.errors);
        this.duration = builder.duration;
    }

    public int getTotalFiles() { return totalFiles; }
    public int getSuccessfulFiles() { return successfulFiles; }
    public int getFailedFiles() { return failedFiles; }
    public List<OrganizedFile> getOrganizedFiles() { return organizedFiles; }
    public List<FileError> getErrors() { return errors; }
    public Duration getDuration() { return duration; }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public boolean allSucceeded() {
        return failedFiles == 0;
    }

    public static class Builder {
        private int totalFiles;
        private int successfulFiles;
        private int failedFiles;
        private List<OrganizedFile> organizedFiles = new java.util.ArrayList<>();
        private List<FileError> errors = new java.util.ArrayList<>();
        private Duration duration = Duration.ZERO;

        public Builder totalFiles(int totalFiles) {
            this.totalFiles = totalFiles;
            return this;
        }

        public Builder successfulFiles(int successfulFiles) {
            this.successfulFiles = successfulFiles;
            return this;
        }

        public Builder failedFiles(int failedFiles) {
            this.failedFiles = failedFiles;
            return this;
        }

        public Builder organizedFiles(List<OrganizedFile> organizedFiles) {
            this.organizedFiles = new java.util.ArrayList<>(organizedFiles);
            return this;
        }

        public Builder addOrganizedFile(OrganizedFile file) {
            this.organizedFiles.add(file);
            return this;
        }

        public Builder errors(List<FileError> errors) {
            this.errors = new java.util.ArrayList<>(errors);
            return this;
        }

        public Builder addError(FileError error) {
            this.errors.add(error);
            return this;
        }

        public Builder duration(Duration duration) {
            this.duration = duration;
            return this;
        }

        public OutputResult build() {
            return new OutputResult(this);
        }
    }

    @Override
    public String toString() {
        return String.format("OutputResult[total=%d, success=%d, failed=%d, duration=%s]",
            totalFiles, successfulFiles, failedFiles, duration);
    }
}
```

---

### 2. FilenameGenerator

**Purpose:** Generate safe, descriptive filenames from voicemail metadata

**File:** `FilenameGenerator.java`

#### Filename Format

```
{YYYYMMDD}-{HHMMSS}-{caller}.{extension}

Examples:
20240312-143022-John_Smith.wav
20240312-143022-+12345678900.wav
20240312-143022-Unknown_Caller.wav
20240312-143022-Unknown_Caller.json
```

#### Implementation

```java
package com.voicemail.output;

import com.voicemail.metadata.VoicemailMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
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
    private static final Logger logger = LoggerFactory.getLogger(FilenameGenerator.class);

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
     * @param metadata voicemail metadata
     * @return filename without collision suffix (e.g., "20240312-143022-John_Smith.wav")
     */
    public String generateWavFilename(VoicemailMetadata metadata) {
        Objects.requireNonNull(metadata, "metadata cannot be null");
        return generateFilename(metadata, "wav");
    }

    /**
     * Generate a filename for a JSON metadata file.
     *
     * @param metadata voicemail metadata
     * @return filename without collision suffix (e.g., "20240312-143022-John_Smith.json")
     */
    public String generateJsonFilename(VoicemailMetadata metadata) {
        Objects.requireNonNull(metadata, "metadata cannot be null");
        return generateFilename(metadata, "json");
    }

    /**
     * Generate a filename for the original audio file.
     *
     * @param metadata voicemail metadata
     * @param originalExtension original file extension (e.g., "amr", "awb")
     * @return filename without collision suffix
     */
    public String generateOriginalFilename(VoicemailMetadata metadata, String originalExtension) {
        Objects.requireNonNull(metadata, "metadata cannot be null");
        Objects.requireNonNull(originalExtension, "originalExtension cannot be null");

        // Remove leading dot if present
        String ext = originalExtension.startsWith(".") ?
            originalExtension.substring(1) : originalExtension;

        return generateFilename(metadata, ext);
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
    public String generateUniqueFilename(String baseFilename, java.util.Set<String> existingNames) {
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
                logger.debug("Resolved filename collision: {} -> {}", baseFilename, candidateFilename);
                return candidateFilename;
            }
        }

        // If we exhaust 1000 attempts, fall back to timestamp
        String fallback = nameWithoutExt + "-" + System.currentTimeMillis() + extension;
        logger.warn("Could not resolve filename collision after 1000 attempts, using timestamp: {}",
            fallback);
        return fallback;
    }

    // Private helper methods

    private String generateFilename(VoicemailMetadata metadata, String extension) {
        String datePart = formatDate(metadata.getReceivedDate());
        String timePart = formatTime(metadata.getReceivedDate());
        String callerPart = sanitizeCallerInfo(metadata);

        String filename = String.format("%s-%s-%s.%s", datePart, timePart, callerPart, extension);

        // Truncate if too long
        if (filename.length() > MAX_FILENAME_LENGTH) {
            int truncateLength = MAX_FILENAME_LENGTH - extension.length() - 1;
            filename = filename.substring(0, truncateLength) + "." + extension;
            logger.debug("Truncated long filename to {} characters", MAX_FILENAME_LENGTH);
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

    private String sanitizeCallerInfo(VoicemailMetadata metadata) {
        String callerInfo = null;

        // Prefer caller name
        if (metadata.getCallerName() != null && !metadata.getCallerName().isEmpty()) {
            callerInfo = metadata.getCallerName();
        }
        // Fall back to phone number
        else if (metadata.getPhoneNumber() != null && !metadata.getPhoneNumber().isEmpty()) {
            callerInfo = metadata.getPhoneNumber();
        }
        // Last resort
        else {
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
}
```

#### Test Cases

```java
@Test
void testGenerateWavFilename_withCallerName() {
    VoicemailMetadata metadata = new VoicemailMetadata.Builder()
        .receivedDate(Instant.parse("2024-03-12T14:30:22Z"))
        .callerName("John Smith")
        .phoneNumber("+12345678900")
        .build();

    String filename = generator.generateWavFilename(metadata);

    // Note: Depends on system timezone
    assertThat(filename).matches("\\d{8}-\\d{6}-John_Smith\\.wav");
}

@Test
void testGenerateWavFilename_withPhoneNumberOnly() {
    VoicemailMetadata metadata = new VoicemailMetadata.Builder()
        .receivedDate(Instant.parse("2024-03-12T14:30:22Z"))
        .phoneNumber("+12345678900")
        .build();

    String filename = generator.generateWavFilename(metadata);

    assertThat(filename).matches("\\d{8}-\\d{6}-\\+12345678900\\.wav");
}

@Test
void testGenerateUniqueFilename_collision() {
    Set<String> existing = Set.of("20240312-143022-John_Smith.wav");

    String unique = generator.generateUniqueFilename(
        "20240312-143022-John_Smith.wav", existing);

    assertThat(unique).isEqualTo("20240312-143022-John_Smith-1.wav");
}

@Test
void testSanitizeCallerInfo_unsafeCharacters() {
    VoicemailMetadata metadata = new VoicemailMetadata.Builder()
        .receivedDate(Instant.now())
        .callerName("John/Smith<Script>")
        .build();

    String filename = generator.generateWavFilename(metadata);

    assertThat(filename).contains("John_Smith_Script_");
}
```

---

### 3. DirectoryCreator

**Purpose:** Create date-based directory structure for output files

**File:** `DirectoryCreator.java`

#### Directory Structure

```
./voicemail-wavs/
└── 2024-03-12/
    ├── 20240312-143022-John_Smith.wav
    ├── 20240312-143022-John_Smith.json
    └── ...

./voicemail-backup/  (if --keep-originals)
└── 2024-03-12/
    ├── 20240312-143022-John_Smith.amr
    └── ...
```

#### Implementation

```java
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
    private static final Logger logger = LoggerFactory.getLogger(DirectoryCreator.class);

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
                logger.info("Created date directory: {}", dateDir);
            } else {
                logger.debug("Date directory already exists: {}", dateDir);
            }

            return dateDir;
        } catch (IOException e) {
            String message = String.format("Failed to create date directory: %s", dateDir);
            logger.error(message, e);
            throw new PermissionException(message, e);
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
            long availableBytes = FileSystemUtil.getAvailableDiskSpace(outputDir);
            boolean sufficient = availableBytes >= requiredBytes;

            if (!sufficient) {
                logger.warn("Insufficient disk space: {} required, {} available",
                    FileSystemUtil.formatBytes(requiredBytes),
                    FileSystemUtil.formatBytes(availableBytes));
            }

            return sufficient;
        } catch (IOException e) {
            logger.warn("Failed to check disk space for {}: {}", outputDir, e.getMessage());
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
                logger.info("Created {} directory: {}", description, directory);
            }

            // Verify directory is writable
            if (!Files.isWritable(directory)) {
                String message = String.format("%s directory is not writable: %s",
                    description, directory);
                logger.error(message);
                throw new PermissionException(message);
            }

            logger.debug("{} directory verified: {}", description, directory);

        } catch (IOException e) {
            String message = String.format("Failed to create %s directory: %s",
                description, directory);
            logger.error(message, e);
            throw new PermissionException(message, e);
        }
    }
}
```

#### Test Cases

```java
@Test
void testCreateDateDirectory() throws Exception {
    Path baseDir = tempDir.resolve("voicemail-wavs");
    Instant timestamp = Instant.parse("2024-03-12T14:30:22Z");

    Path dateDir = creator.createDateDirectory(baseDir, timestamp);

    assertThat(Files.exists(dateDir)).isTrue();
    assertThat(Files.isDirectory(dateDir)).isTrue();
    assertThat(dateDir.getFileName().toString()).matches("\\d{4}-\\d{2}-\\d{2}");
}

@Test
void testEnsureBaseDirectoriesExist_bothDirectories() throws Exception {
    Path wavDir = tempDir.resolve("voicemail-wavs");
    Path backupDir = tempDir.resolve("voicemail-backup");

    creator.ensureBaseDirectoriesExist(wavDir, backupDir);

    assertThat(Files.exists(wavDir)).isTrue();
    assertThat(Files.exists(backupDir)).isTrue();
    assertThat(Files.isWritable(wavDir)).isTrue();
    assertThat(Files.isWritable(backupDir)).isTrue();
}
```

---

### 4. OriginalFileKeeper

**Purpose:** Handle copying of original audio files when `--keep-originals` flag is set

**File:** `OriginalFileKeeper.java`

```java
package com.voicemail.output;

import com.voicemail.metadata.VoicemailMetadata;
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
    private static final Logger logger = LoggerFactory.getLogger(OriginalFileKeeper.class);

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
     * @param metadata voicemail metadata
     * @param backupDir base backup directory (e.g., "./voicemail-backup")
     * @return path to copied file, or null if backupDir is null
     * @throws IOException if copy operation fails
     */
    public Path copyOriginalFile(Path originalFile, VoicemailMetadata metadata, Path backupDir)
            throws IOException {
        Objects.requireNonNull(originalFile, "originalFile cannot be null");
        Objects.requireNonNull(metadata, "metadata cannot be null");

        // If backup directory not specified, skip
        if (backupDir == null) {
            logger.debug("Backup directory not specified, skipping original file copy");
            return null;
        }

        // Ensure original file exists
        if (!Files.exists(originalFile)) {
            logger.warn("Original file does not exist, cannot copy: {}", originalFile);
            return null;
        }

        try {
            // Create date subdirectory
            Path dateDir = directoryCreator.createDateDirectory(
                backupDir, metadata.getReceivedDate());

            // Get original file extension
            String originalExtension = getFileExtension(originalFile);

            // Generate filename
            String filename = filenameGenerator.generateOriginalFilename(
                metadata, originalExtension);

            // Build destination path
            Path destinationPath = dateDir.resolve(filename);

            // Copy file
            Files.copy(originalFile, destinationPath, StandardCopyOption.REPLACE_EXISTING);

            logger.info("Copied original file: {} -> {}",
                originalFile.getFileName(), destinationPath);

            return destinationPath;

        } catch (IOException e) {
            String message = String.format("Failed to copy original file %s to %s",
                originalFile, backupDir);
            logger.error(message, e);
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
```

---

### 5. FileOrganizer (Main Orchestrator)

**Purpose:** Main orchestrator that coordinates all output operations

**File:** `FileOrganizer.java`

```java
package com.voicemail.output;

import com.voicemail.converter.ConversionResult;
import com.voicemail.exception.PermissionException;
import com.voicemail.metadata.VoicemailMetadata;
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
    private static final Logger logger = LoggerFactory.getLogger(FileOrganizer.class);

    private final FilenameGenerator filenameGenerator;
    private final DirectoryCreator directoryCreator;
    private final OriginalFileKeeper originalFileKeeper;

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
     * @param conversionResults list of conversion results from AudioConverter
     * @param wavOutputDir base directory for WAV files (e.g., "./voicemail-wavs")
     * @param backupDir base directory for originals (null if --keep-originals not set)
     * @param includeMetadata whether to copy JSON metadata files
     * @return result with statistics and error information
     * @throws PermissionException if output directories cannot be created
     */
    public OutputResult organizeFiles(List<ConversionResult> conversionResults,
                                      Path wavOutputDir,
                                      Path backupDir,
                                      boolean includeMetadata) throws PermissionException {
        Objects.requireNonNull(conversionResults, "conversionResults cannot be null");
        Objects.requireNonNull(wavOutputDir, "wavOutputDir cannot be null");

        logger.info("Organizing {} converted files to output directories",
            conversionResults.size());

        Instant startTime = Instant.now();

        // Ensure base directories exist
        directoryCreator.ensureBaseDirectoriesExist(wavOutputDir, backupDir);

        // Build result
        OutputResult.Builder resultBuilder = new OutputResult.Builder()
            .totalFiles(conversionResults.size());

        // Track existing filenames to handle collisions
        Set<String> existingWavFilenames = new HashSet<>();
        Set<String> existingBackupFilenames = new HashSet<>();

        int successCount = 0;
        int failureCount = 0;

        for (ConversionResult conversionResult : conversionResults) {
            try {
                OrganizedFile organized = organizeFile(
                    conversionResult,
                    wavOutputDir,
                    backupDir,
                    includeMetadata,
                    existingWavFilenames,
                    existingBackupFilenames
                );

                resultBuilder.addOrganizedFile(organized);
                successCount++;

            } catch (Exception e) {
                logger.error("Failed to organize file: {}",
                    conversionResult.getOutputFile(), e);

                FileError error = new FileError(
                    conversionResult.getOutputFile(),
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

        logger.info("File organization complete: {} succeeded, {} failed in {}",
            successCount, failureCount, duration);

        return result;
    }

    // Private helper methods

    private OrganizedFile organizeFile(ConversionResult conversionResult,
                                       Path wavOutputDir,
                                       Path backupDir,
                                       boolean includeMetadata,
                                       Set<String> existingWavFilenames,
                                       Set<String> existingBackupFilenames)
            throws IOException, PermissionException {

        VoicemailMetadata metadata = conversionResult.getMetadata();
        Path wavFile = conversionResult.getOutputFile();
        Path jsonFile = conversionResult.getMetadataFile();
        Path originalFile = conversionResult.getInputFile();

        // Create date subdirectory for WAV output
        Path wavDateDir = directoryCreator.createDateDirectory(
            wavOutputDir, metadata.getReceivedDate());

        // Generate base filename
        String baseWavFilename = filenameGenerator.generateWavFilename(metadata);
        String uniqueWavFilename = filenameGenerator.generateUniqueFilename(
            baseWavFilename, existingWavFilenames);
        existingWavFilenames.add(uniqueWavFilename);

        // Copy WAV file
        Path wavDestination = wavDateDir.resolve(uniqueWavFilename);
        Files.copy(wavFile, wavDestination, StandardCopyOption.REPLACE_EXISTING);
        logger.debug("Copied WAV file: {} -> {}", wavFile.getFileName(), wavDestination);

        // Copy JSON metadata if enabled
        Path jsonDestination = null;
        if (includeMetadata && jsonFile != null && Files.exists(jsonFile)) {
            String jsonFilename = uniqueWavFilename.replace(".wav", ".json");
            jsonDestination = wavDateDir.resolve(jsonFilename);
            Files.copy(jsonFile, jsonDestination, StandardCopyOption.REPLACE_EXISTING);
            logger.debug("Copied JSON file: {} -> {}", jsonFile.getFileName(), jsonDestination);
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
                metadata, originalExtension);
            String uniqueOriginalFilename = filenameGenerator.generateUniqueFilename(
                baseOriginalFilename, existingBackupFilenames);
            existingBackupFilenames.add(uniqueOriginalFilename);

            Path backupDateDir = directoryCreator.createDateDirectory(
                backupDir, metadata.getReceivedDate());
            originalDestination = backupDateDir.resolve(uniqueOriginalFilename);
            Files.copy(originalFile, originalDestination, StandardCopyOption.REPLACE_EXISTING);
            logger.debug("Copied original file: {} -> {}",
                originalFile.getFileName(), originalDestination);
        }

        return new OrganizedFile(
            wavDestination,
            jsonDestination,
            originalDestination,
            metadata.getCallerName() != null ? metadata.getCallerName() : metadata.getPhoneNumber(),
            metadata.getReceivedDate().toString()
        );
    }

    private String getFileExtension(Path file) {
        String filename = file.getFileName().toString();
        int dotIndex = filename.lastIndexOf('.');
        return (dotIndex > 0 && dotIndex < filename.length() - 1) ?
            filename.substring(dotIndex + 1) : "amr";
    }
}
```

---

## Testing Strategy

### Unit Tests

```java
package com.voicemail.output;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class FilenameGeneratorTest {
    private FilenameGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new FilenameGenerator();
    }

    @Test
    void testGenerateWavFilename_withCallerName() {
        // Test implementation
    }

    @Test
    void testGenerateUniqueFilename_handlesCollisions() {
        // Test implementation
    }
}

class DirectoryCreatorTest {
    @TempDir
    Path tempDir;

    private DirectoryCreator creator;

    @BeforeEach
    void setUp() {
        creator = new DirectoryCreator();
    }

    @Test
    void testCreateDateDirectory() throws Exception {
        // Test implementation
    }
}

class FileOrganizerTest {
    @TempDir
    Path tempDir;

    private FileOrganizer organizer;

    @BeforeEach
    void setUp() {
        organizer = new FileOrganizer();
    }

    @Test
    void testOrganizeFiles_success() throws Exception {
        // Test implementation
    }
}
```

### Integration Tests

```java
@Test
void testEndToEndFileOrganization() throws Exception {
    // Given: Conversion results with various metadata
    List<ConversionResult> results = createTestConversionResults();
    Path wavDir = tempDir.resolve("voicemail-wavs");
    Path backupDir = tempDir.resolve("voicemail-backup");

    // When: Organize files
    OutputResult result = organizer.organizeFiles(results, wavDir, backupDir, true);

    // Then: Verify directory structure
    assertThat(Files.list(wavDir).count()).isGreaterThan(0);
    assertThat(Files.list(backupDir).count()).isGreaterThan(0);

    // Verify date subdirectories exist
    Path dateDir = wavDir.resolve("2024-03-12");
    assertThat(Files.exists(dateDir)).isTrue();

    // Verify files were copied
    assertThat(result.getSuccessfulFiles()).isEqualTo(results.size());
    assertThat(result.getFailedFiles()).isZero();
}
```

---

## Error Handling

### Exception Scenarios

| Scenario | Exception Type | Handling Strategy |
|----------|---------------|-------------------|
| Cannot create output directory | `PermissionException` | Throw immediately, stop processing |
| Output directory not writable | `PermissionException` | Throw immediately, stop processing |
| Insufficient disk space | `InsufficientStorageException` | Throw before processing |
| File copy fails | `IOException` | Log error, add to failures, continue |
| Filename collision (>1000 attempts) | N/A | Use timestamp fallback |

---

## Logging Strategy

```java
// INFO: Key operations
logger.info("Organizing {} converted files to output directories", count);
logger.info("Created date directory: {}", dateDir);
logger.info("File organization complete: {} succeeded, {} failed", success, failed);

// DEBUG: Detailed operations
logger.debug("Date directory already exists: {}", dateDir);
logger.debug("Copied WAV file: {} -> {}", source, destination);
logger.debug("Resolved filename collision: {} -> {}", base, unique);

// WARN: Recoverable issues
logger.warn("Original file does not exist, cannot copy: {}", file);
logger.warn("Insufficient disk space: {} required, {} available", required, available);

// ERROR: Failures
logger.error("Failed to create date directory: {}", dir, exception);
logger.error("Failed to organize file: {}", file, exception);
```

---

## Performance Considerations

1. **Batch Operations** - Process all files in single pass
2. **Set-Based Collision Detection** - O(1) filename lookups
3. **Lazy Directory Creation** - Create subdirectories as needed
4. **Atomic File Operations** - Use `StandardCopyOption.REPLACE_EXISTING`
5. **Memory Efficient** - Stream file copies, don't load into memory

---

## Dependencies

### Internal

```java
import com.voicemail.exception.PermissionException;
import com.voicemail.exception.InsufficientStorageException;
import com.voicemail.util.FileSystemUtil;
import com.voicemail.converter.ConversionResult;
import com.voicemail.metadata.VoicemailMetadata;
```

### External

```java
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
```

---

## Usage Example

```java
// Create organizer
FileOrganizer organizer = new FileOrganizer();

// Organize files after conversion
OutputResult result = organizer.organizeFiles(
    conversionResults,
    Paths.get("./voicemail-wavs"),
    Paths.get("./voicemail-backup"),  // null if --keep-originals not set
    true  // includeMetadata
);

// Check results
if (result.allSucceeded()) {
    System.out.println("Successfully organized " + result.getSuccessfulFiles() + " files");
} else {
    System.err.println("Failed to organize " + result.getFailedFiles() + " files");
    for (OutputResult.FileError error : result.getErrors()) {
        System.err.println("  - " + error.getSourceFile() + ": " + error.getErrorMessage());
    }
}
```

---

## Implementation Checklist

- [ ] `OutputResult` - Result data class with nested classes
- [ ] `FilenameGenerator` - Safe filename generation
- [ ] `DirectoryCreator` - Date-based directory structure
- [ ] `OriginalFileKeeper` - Original file preservation
- [ ] `FileOrganizer` - Main orchestrator
- [ ] Unit tests for all classes
- [ ] Integration tests for end-to-end flow
- [ ] Error handling and logging
- [ ] JavaDoc documentation

---

## Estimated Effort

- **Implementation:** 4 hours
- **Testing:** 2 hours
- **Documentation:** 30 minutes
- **Total:** ~7 hours

---

**End of Output Module Implementation Guide**
