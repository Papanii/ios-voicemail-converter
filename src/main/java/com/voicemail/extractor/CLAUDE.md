# Extractor Module Implementation Guide

**Module:** Voicemail Extraction
**Package:** `com.voicemail.extractor`
**Status:** Not Implemented
**Priority:** High (Core functionality)

---

## Overview

The Extractor module is responsible for extracting voicemail files and metadata from iOS backups. This is the most complex module as it deals with iOS backup file structure, SQLite databases, and SHA-1 hashing.

## Module Purpose

Handle:
1. Reading Manifest.db (SQLite file catalog)
2. Reading voicemail.db (voicemail metadata)
3. Computing SHA-1 hashes for file lookup
4. Extracting audio files from backup
5. Pairing audio files with metadata

---

## Dependencies

### External Libraries
- **SQLite JDBC 3.44.1** - Database access (already in pom.xml)

### Internal Dependencies
- `com.voicemail.backup.BackupInfo` - Backup information
- `com.voicemail.exception.*` - Exception classes
- `com.voicemail.util.*` - Utility classes (especially HashUtil)

---

## iOS Backup File Structure Primer

**Key Concept:** iOS backups use SHA-1 hashing for file storage.

```
Formula: SHA-1(domain + "-" + relativePath) = fileHash
Storage: {fileHash[0:2]}/{fileHash}

Example:
  Domain: "Library-Voicemail"
  Path: "voicemail.db"
  Combined: "Library-Voicemail-voicemail.db"
  Hash: 3d0d7e5fb2ce288813306e4d4636395e047a3d28
  Location: backup/3d/3d0d7e5fb2ce288813306e4d4636395e047a3d28
```

---

## Implementation Order

1. **VoicemailFile** - Data class for extracted files (first)
2. **ManifestDbReader** - Read Manifest.db
3. **VoicemailDbReader** - Read voicemail.db
4. **FileExtractor** - Extract files from backup
5. **FileMatcher** - Pair audio with metadata
6. **VoicemailExtractor** - Main orchestrator (last)

---

## Class 1: VoicemailFile

**Location:** `src/main/java/com/voicemail/extractor/VoicemailFile.java`

### Implementation

```java
package com.voicemail.extractor;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;

/**
 * Represents an extracted voicemail file with metadata
 */
public class VoicemailFile {
    private final String fileId;              // SHA-1 hash from Manifest.db
    private final String domain;              // e.g., "Library-Voicemail"
    private final String relativePath;        // Original iOS path
    private final Path backupFilePath;        // Path in backup (e.g., ab/ab123...)
    private final Path extractedPath;         // Path in temp directory
    private final VoicemailMetadata metadata; // Associated metadata (may be null)
    private final AudioFormat format;         // AMR-NB, AMR-WB, AAC
    private final long fileSize;              // Size in bytes

    public enum AudioFormat {
        AMR_NB(".amr", "AMR Narrowband"),
        AMR_WB(".awb", "AMR Wideband"),
        AAC(".m4a", "AAC"),
        UNKNOWN(".bin", "Unknown");

        private final String extension;
        private final String description;

        AudioFormat(String extension, String description) {
            this.extension = extension;
            this.description = description;
        }

        public String getExtension() {
            return extension;
        }

        public String getDescription() {
            return description;
        }

        public static AudioFormat fromExtension(String ext) {
            for (AudioFormat format : values()) {
                if (format.extension.equalsIgnoreCase(ext)) {
                    return format;
                }
            }
            return UNKNOWN;
        }
    }

    /**
     * Metadata from voicemail.db
     */
    public static class VoicemailMetadata {
        private final long rowId;
        private final long remoteUid;
        private final Instant receivedDate;
        private final String callerNumber;
        private final String callbackNumber;
        private final int durationSeconds;
        private final Instant expirationDate;
        private final Instant trashedDate;
        private final int flags;
        private final boolean isRead;
        private final boolean isSpam;

        public VoicemailMetadata(long rowId, long remoteUid, Instant receivedDate,
                                String callerNumber, String callbackNumber,
                                int durationSeconds, Instant expirationDate,
                                Instant trashedDate, int flags) {
            this.rowId = rowId;
            this.remoteUid = remoteUid;
            this.receivedDate = receivedDate;
            this.callerNumber = callerNumber;
            this.callbackNumber = callbackNumber;
            this.durationSeconds = durationSeconds;
            this.expirationDate = expirationDate;
            this.trashedDate = trashedDate;
            this.flags = flags;
            this.isRead = (flags & 0x01) != 0;
            this.isSpam = (flags & 0x04) != 0;
        }

        // Getters
        public long getRowId() { return rowId; }
        public long getRemoteUid() { return remoteUid; }
        public Instant getReceivedDate() { return receivedDate; }
        public String getCallerNumber() { return callerNumber; }
        public String getCallbackNumber() { return callbackNumber; }
        public int getDurationSeconds() { return durationSeconds; }
        public Instant getExpirationDate() { return expirationDate; }
        public Instant getTrashedDate() { return trashedDate; }
        public int getFlags() { return flags; }
        public boolean isRead() { return isRead; }
        public boolean isSpam() { return isSpam; }
        public boolean wasTrashed() { return trashedDate != null; }

        @Override
        public String toString() {
            return String.format("Voicemail[caller=%s, date=%s, duration=%ds]",
                callerNumber != null ? callerNumber : "Unknown",
                receivedDate,
                durationSeconds
            );
        }
    }

    private VoicemailFile(Builder builder) {
        this.fileId = Objects.requireNonNull(builder.fileId);
        this.domain = builder.domain;
        this.relativePath = Objects.requireNonNull(builder.relativePath);
        this.backupFilePath = Objects.requireNonNull(builder.backupFilePath);
        this.extractedPath = builder.extractedPath;
        this.metadata = builder.metadata;
        this.format = builder.format;
        this.fileSize = builder.fileSize;
    }

    // Getters
    public String getFileId() { return fileId; }
    public String getDomain() { return domain; }
    public String getRelativePath() { return relativePath; }
    public Path getBackupFilePath() { return backupFilePath; }
    public Path getExtractedPath() { return extractedPath; }
    public VoicemailMetadata getMetadata() { return metadata; }
    public AudioFormat getFormat() { return format; }
    public long getFileSize() { return fileSize; }

    public boolean hasMetadata() {
        return metadata != null;
    }

    /**
     * Get filename from relative path
     */
    public String getOriginalFilename() {
        if (relativePath == null) {
            return "unknown";
        }
        int lastSlash = relativePath.lastIndexOf('/');
        return lastSlash >= 0 ? relativePath.substring(lastSlash + 1) : relativePath;
    }

    @Override
    public String toString() {
        return String.format("VoicemailFile[%s, %s, %s]",
            getOriginalFilename(),
            format.getDescription(),
            metadata != null ? metadata.toString() : "no metadata"
        );
    }

    /**
     * Builder for VoicemailFile
     */
    public static class Builder {
        private String fileId;
        private String domain;
        private String relativePath;
        private Path backupFilePath;
        private Path extractedPath;
        private VoicemailMetadata metadata;
        private AudioFormat format = AudioFormat.UNKNOWN;
        private long fileSize;

        public Builder fileId(String fileId) {
            this.fileId = fileId;
            return this;
        }

        public Builder domain(String domain) {
            this.domain = domain;
            return this;
        }

        public Builder relativePath(String relativePath) {
            this.relativePath = relativePath;
            // Auto-detect format from extension
            if (relativePath != null) {
                int dotIndex = relativePath.lastIndexOf('.');
                if (dotIndex > 0) {
                    String ext = relativePath.substring(dotIndex);
                    this.format = AudioFormat.fromExtension(ext);
                }
            }
            return this;
        }

        public Builder backupFilePath(Path backupFilePath) {
            this.backupFilePath = backupFilePath;
            return this;
        }

        public Builder extractedPath(Path extractedPath) {
            this.extractedPath = extractedPath;
            return this;
        }

        public Builder metadata(VoicemailMetadata metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder format(AudioFormat format) {
            this.format = format;
            return this;
        }

        public Builder fileSize(long fileSize) {
            this.fileSize = fileSize;
            return this;
        }

        public VoicemailFile build() {
            return new VoicemailFile(this);
        }
    }
}
```

---

## Class 2: ManifestDbReader

**Location:** `src/main/java/com/voicemail/extractor/ManifestDbReader.java`

### Implementation

```java
package com.voicemail.extractor;

import com.voicemail.exception.BackupException;
import com.voicemail.util.HashUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads iOS Manifest.db (SQLite file catalog)
 */
public class ManifestDbReader {
    private static final Logger log = LoggerFactory.getLogger(ManifestDbReader.class);

    private final Path manifestDbPath;
    private Connection connection;

    public ManifestDbReader(Path backupPath) {
        this.manifestDbPath = backupPath.resolve("Manifest.db");
    }

    /**
     * Open connection to Manifest.db
     */
    public void open() throws SQLException {
        log.debug("Opening Manifest.db: {}", manifestDbPath);
        String url = "jdbc:sqlite:" + manifestDbPath.toAbsolutePath();
        connection = DriverManager.getConnection(url);
    }

    /**
     * Close connection
     */
    public void close() {
        if (connection != null) {
            try {
                connection.close();
                log.debug("Closed Manifest.db connection");
            } catch (SQLException e) {
                log.warn("Error closing Manifest.db", e);
            }
        }
    }

    /**
     * Find file in manifest by domain and relative path
     */
    public String findFileId(String domain, String relativePath) throws SQLException {
        String hash = HashUtil.calculateBackupFileHash(domain, relativePath);
        log.debug("Looking for file: {} (hash: {})", relativePath, hash);

        String sql = "SELECT fileID FROM Files WHERE fileID = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, hash);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                String fileId = rs.getString("fileID");
                log.debug("Found file: {}", fileId);
                return fileId;
            }
        }

        log.debug("File not found in manifest: {}", relativePath);
        return null;
    }

    /**
     * Query all voicemail audio files
     */
    public List<FileInfo> queryVoicemailFiles() throws SQLException {
        log.info("Querying voicemail files from Manifest.db");

        List<FileInfo> files = new ArrayList<>();

        String sql = "SELECT fileID, domain, relativePath " +
                    "FROM Files " +
                    "WHERE domain = 'Library-Voicemail' " +
                    "  AND (relativePath LIKE 'voicemail/%.amr' " +
                    "       OR relativePath LIKE 'voicemail/%.awb' " +
                    "       OR relativePath LIKE 'voicemail/%.m4a') " +
                    "ORDER BY relativePath";

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                String fileId = rs.getString("fileID");
                String domain = rs.getString("domain");
                String relativePath = rs.getString("relativePath");

                files.add(new FileInfo(fileId, domain, relativePath));
                log.debug("Found voicemail file: {}", relativePath);
            }
        }

        log.info("Found {} voicemail audio files", files.size());
        return files;
    }

    /**
     * Simple data class for file information
     */
    public static class FileInfo {
        public final String fileId;
        public final String domain;
        public final String relativePath;

        public FileInfo(String fileId, String domain, String relativePath) {
            this.fileId = fileId;
            this.domain = domain;
            this.relativePath = relativePath;
        }

        @Override
        public String toString() {
            return String.format("FileInfo[%s: %s]", fileId, relativePath);
        }
    }
}
```

---

## Class 3: VoicemailDbReader

**Location:** `src/main/java/com/voicemail/extractor/VoicemailDbReader.java`

### Implementation

```java
package com.voicemail.extractor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads voicemail.db (voicemail metadata database)
 */
public class VoicemailDbReader {
    private static final Logger log = LoggerFactory.getLogger(VoicemailDbReader.class);

    private final Path voicemailDbPath;
    private Connection connection;

    public VoicemailDbReader(Path voicemailDbPath) {
        this.voicemailDbPath = voicemailDbPath;
    }

    /**
     * Open connection to voicemail.db
     */
    public void open() throws SQLException {
        log.debug("Opening voicemail.db: {}", voicemailDbPath);
        String url = "jdbc:sqlite:" + voicemailDbPath.toAbsolutePath();
        connection = DriverManager.getConnection(url);
    }

    /**
     * Close connection
     */
    public void close() {
        if (connection != null) {
            try {
                connection.close();
                log.debug("Closed voicemail.db connection");
            } catch (SQLException e) {
                log.warn("Error closing voicemail.db", e);
            }
        }
    }

    /**
     * Read all voicemail metadata
     */
    public List<VoicemailFile.VoicemailMetadata> readAllMetadata(boolean includeTrashed)
            throws SQLException {

        log.info("Reading voicemail metadata (includeTrashed={})", includeTrashed);

        List<VoicemailFile.VoicemailMetadata> metadataList = new ArrayList<>();

        String sql = "SELECT ROWID, remote_uid, date, sender, callback_num, " +
                    "       duration, expiration, trashed_date, flags " +
                    "FROM voicemail ";

        if (!includeTrashed) {
            sql += "WHERE trashed_date IS NULL ";
        }

        sql += "ORDER BY date DESC";

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                long rowId = rs.getLong("ROWID");
                long remoteUid = rs.getLong("remote_uid");

                // Date is Unix timestamp
                long dateUnix = rs.getLong("date");
                Instant receivedDate = Instant.ofEpochSecond(dateUnix);

                String sender = rs.getString("sender");
                String callbackNum = rs.getString("callback_num");
                int duration = rs.getInt("duration");

                // Expiration
                long expUnix = rs.getLong("expiration");
                Instant expirationDate = expUnix > 0 ? Instant.ofEpochSecond(expUnix) : null;

                // Trashed date
                long trashedUnix = rs.getLong("trashed_date");
                Instant trashedDate = trashedUnix > 0 ? Instant.ofEpochSecond(trashedUnix) : null;

                int flags = rs.getInt("flags");

                VoicemailFile.VoicemailMetadata metadata = new VoicemailFile.VoicemailMetadata(
                    rowId, remoteUid, receivedDate, sender, callbackNum,
                    duration, expirationDate, trashedDate, flags
                );

                metadataList.add(metadata);
                log.debug("Parsed metadata: {}", metadata);
            }
        }

        log.info("Read {} voicemail metadata records", metadataList.size());
        return metadataList;
    }

    /**
     * Check if database exists and has voicemail table
     */
    public static boolean isValidVoicemailDb(Path dbPath) {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
            DatabaseMetaData meta = conn.getMetaData();
            ResultSet rs = meta.getTables(null, null, "voicemail", null);
            return rs.next();
        } catch (SQLException e) {
            log.warn("Invalid voicemail.db: {}", dbPath, e);
            return false;
        }
    }
}
```

---

## Class 4: FileExtractor

**Location:** `src/main/java/com/voicemail/extractor/FileExtractor.java`

### Implementation

```java
package com.voicemail.extractor;

import com.voicemail.util.FileSystemUtil;
import com.voicemail.util.HashUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Extracts files from iOS backup to temp directory
 */
public class FileExtractor {
    private static final Logger log = LoggerFactory.getLogger(FileExtractor.class);

    private final Path backupPath;
    private final Path tempDirectory;

    public FileExtractor(Path backupPath, Path tempDirectory) {
        this.backupPath = backupPath;
        this.tempDirectory = tempDirectory;
    }

    /**
     * Extract file from backup by hash
     */
    public Path extractFile(String fileHash, String outputName) throws IOException {
        // Compute backup file path: {hash[0:2]}/{hash}
        String backupFilePath = HashUtil.getBackupFilePath(fileHash);
        Path sourceFile = backupPath.resolve(backupFilePath);

        if (!Files.exists(sourceFile)) {
            log.warn("Backup file not found: {}", sourceFile);
            return null;
        }

        // Extract to temp directory
        Path destination = tempDirectory.resolve(outputName);
        log.debug("Extracting: {} -> {}", backupFilePath, destination);

        FileSystemUtil.copyFile(sourceFile, destination);

        long size = Files.size(destination);
        log.debug("Extracted {} bytes", size);

        return destination;
    }

    /**
     * Extract file by domain and relative path
     */
    public Path extractFile(String domain, String relativePath, String outputName)
            throws IOException {

        String hash = HashUtil.calculateBackupFileHash(domain, relativePath);
        return extractFile(hash, outputName);
    }

    /**
     * Extract voicemail.db to temp directory
     */
    public Path extractVoicemailDb() throws IOException {
        log.info("Extracting voicemail.db");

        String hash = HashUtil.calculateBackupFileHash("Library-Voicemail", "voicemail.db");
        Path extracted = extractFile(hash, "voicemail.db");

        if (extracted == null) {
            log.warn("voicemail.db not found in backup");
            return null;
        }

        // Verify it's a valid SQLite database
        if (!VoicemailDbReader.isValidVoicemailDb(extracted)) {
            log.error("Extracted voicemail.db is not valid");
            return null;
        }

        log.info("Successfully extracted voicemail.db");
        return extracted;
    }

    /**
     * Get backup file path for hash
     */
    public Path getBackupFilePath(String fileHash) {
        String relativePath = HashUtil.getBackupFilePath(fileHash);
        return backupPath.resolve(relativePath);
    }

    /**
     * Check if file exists in backup
     */
    public boolean fileExistsInBackup(String fileHash) {
        Path file = getBackupFilePath(fileHash);
        return Files.exists(file);
    }
}
```

---

## Class 5: FileMatcher

**Location:** `src/main/java/com/voicemail/extractor/FileMatcher.java`

### Implementation

```java
package com.voicemail.extractor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Matches audio files with metadata based on timestamps
 */
public class FileMatcher {
    private static final Logger log = LoggerFactory.getLogger(FileMatcher.class);

    // Pattern for timestamp in filename: 1699123456.amr
    private static final Pattern TIMESTAMP_PATTERN = Pattern.compile("^(\\d{10})");

    // Tolerance for timestamp matching (±5 seconds)
    private static final long TIMESTAMP_TOLERANCE_SECONDS = 5;

    /**
     * Pair audio files with metadata
     */
    public List<VoicemailFile> matchFilesWithMetadata(
            List<VoicemailFile.Builder> audioFiles,
            List<VoicemailFile.VoicemailMetadata> metadataList) {

        log.info("Matching {} audio files with {} metadata records",
            audioFiles.size(), metadataList.size());

        List<VoicemailFile> matched = new ArrayList<>();
        List<VoicemailFile.VoicemailMetadata> unmatchedMetadata = new ArrayList<>(metadataList);

        for (VoicemailFile.Builder fileBuilder : audioFiles) {
            String filename = extractFilename(fileBuilder);
            Instant fileTimestamp = extractTimestampFromFilename(filename);

            VoicemailFile.VoicemailMetadata bestMatch = null;

            if (fileTimestamp != null) {
                bestMatch = findMatchingMetadata(fileTimestamp, unmatchedMetadata);

                if (bestMatch != null) {
                    unmatchedMetadata.remove(bestMatch);
                    log.debug("Matched {} with metadata: {}", filename, bestMatch);
                } else {
                    log.debug("No metadata match for {} (timestamp: {})", filename, fileTimestamp);
                }
            } else {
                log.debug("Could not extract timestamp from filename: {}", filename);
            }

            // Build file with or without metadata
            VoicemailFile file = fileBuilder.metadata(bestMatch).build();
            matched.add(file);
        }

        log.info("Matched {} files, {} unmatched metadata, {} unmatched files",
            matched.stream().filter(VoicemailFile::hasMetadata).count(),
            unmatchedMetadata.size(),
            matched.stream().filter(f -> !f.hasMetadata()).count()
        );

        return matched;
    }

    /**
     * Extract timestamp from filename
     */
    private Instant extractTimestampFromFilename(String filename) {
        Matcher matcher = TIMESTAMP_PATTERN.matcher(filename);
        if (matcher.find()) {
            try {
                long unixTimestamp = Long.parseLong(matcher.group(1));
                return Instant.ofEpochSecond(unixTimestamp);
            } catch (NumberFormatException e) {
                log.warn("Invalid timestamp in filename: {}", filename);
            }
        }
        return null;
    }

    /**
     * Find metadata matching timestamp
     */
    private VoicemailFile.VoicemailMetadata findMatchingMetadata(
            Instant fileTimestamp,
            List<VoicemailFile.VoicemailMetadata> metadataList) {

        VoicemailFile.VoicemailMetadata bestMatch = null;
        long bestDiff = Long.MAX_VALUE;

        for (VoicemailFile.VoicemailMetadata metadata : metadataList) {
            if (metadata.getReceivedDate() == null) {
                continue;
            }

            Duration diff = Duration.between(fileTimestamp, metadata.getReceivedDate()).abs();
            long diffSeconds = diff.getSeconds();

            if (diffSeconds <= TIMESTAMP_TOLERANCE_SECONDS && diffSeconds < bestDiff) {
                bestMatch = metadata;
                bestDiff = diffSeconds;
            }
        }

        return bestMatch;
    }

    /**
     * Extract filename from builder
     */
    private String extractFilename(VoicemailFile.Builder builder) {
        // This is a bit hacky, but we need access to relativePath
        // In real implementation, you might pass filename separately
        VoicemailFile temp = builder.build();
        return temp.getOriginalFilename();
    }

    /**
     * Create synthetic metadata for unmatched files
     */
    public static VoicemailFile.VoicemailMetadata createSyntheticMetadata(Instant timestamp) {
        return new VoicemailFile.VoicemailMetadata(
            0,              // rowId
            0,              // remoteUid
            timestamp,      // receivedDate
            "Unknown",      // callerNumber
            null,           // callbackNumber
            0,              // duration
            null,           // expirationDate
            null,           // trashedDate
            0               // flags
        );
    }
}
```

---

## Class 6: VoicemailExtractor

**Location:** `src/main/java/com/voicemail/extractor/VoicemailExtractor.java`

### Implementation

```java
package com.voicemail.extractor;

import com.voicemail.backup.BackupInfo;
import com.voicemail.exception.BackupException;
import com.voicemail.exception.NoVoicemailsException;
import com.voicemail.util.TempDirectoryManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Main orchestrator for voicemail extraction
 */
public class VoicemailExtractor {
    private static final Logger log = LoggerFactory.getLogger(VoicemailExtractor.class);

    private final BackupInfo backup;
    private final TempDirectoryManager tempManager;

    public VoicemailExtractor(BackupInfo backup, TempDirectoryManager tempManager) {
        this.backup = backup;
        this.tempManager = tempManager;
    }

    /**
     * Extract all voicemails from backup
     */
    public List<VoicemailFile> extractVoicemails() throws Exception {
        log.info("Starting voicemail extraction from backup");

        // Open Manifest.db
        ManifestDbReader manifestReader = new ManifestDbReader(backup.getBackupPath());
        try {
            manifestReader.open();

            // Extract voicemail.db
            Path voicemailDb = extractVoicemailDatabase(manifestReader);

            // Read metadata if available
            List<VoicemailFile.VoicemailMetadata> metadataList = new ArrayList<>();
            if (voicemailDb != null) {
                metadataList = readVoicemailMetadata(voicemailDb);
            } else {
                log.warn("No voicemail.db found, will use file-only mode");
            }

            // Query audio files from manifest
            List<ManifestDbReader.FileInfo> audioFileInfos = manifestReader.queryVoicemailFiles();

            if (audioFileInfos.isEmpty()) {
                throw new NoVoicemailsException();
            }

            // Extract audio files
            List<VoicemailFile.Builder> audioBuilders = extractAudioFiles(
                manifestReader, audioFileInfos
            );

            // Match files with metadata
            FileMatcher matcher = new FileMatcher();
            List<VoicemailFile> voicemails = matcher.matchFilesWithMetadata(
                audioBuilders, metadataList
            );

            log.info("Successfully extracted {} voicemails", voicemails.size());
            return voicemails;

        } finally {
            manifestReader.close();
        }
    }

    /**
     * Extract voicemail.db from backup
     */
    private Path extractVoicemailDatabase(ManifestDbReader manifestReader) throws Exception {
        log.info("Extracting voicemail database");

        Path tempDir = tempManager.getTempDirectory();
        FileExtractor extractor = new FileExtractor(backup.getBackupPath(), tempDir);

        return extractor.extractVoicemailDb();
    }

    /**
     * Read metadata from voicemail.db
     */
    private List<VoicemailFile.VoicemailMetadata> readVoicemailMetadata(Path voicemailDb)
            throws SQLException {

        log.info("Reading voicemail metadata");

        VoicemailDbReader reader = new VoicemailDbReader(voicemailDb);
        try {
            reader.open();
            // Don't include trashed voicemails by default
            return reader.readAllMetadata(false);
        } finally {
            reader.close();
        }
    }

    /**
     * Extract audio files from backup
     */
    private List<VoicemailFile.Builder> extractAudioFiles(
            ManifestDbReader manifestReader,
            List<ManifestDbReader.FileInfo> audioFileInfos) throws Exception {

        log.info("Extracting {} audio files", audioFileInfos.size());

        Path tempDir = tempManager.createSubdirectory("audio");
        FileExtractor extractor = new FileExtractor(backup.getBackupPath(), tempDir);

        List<VoicemailFile.Builder> builders = new ArrayList<>();

        for (ManifestDbReader.FileInfo fileInfo : audioFileInfos) {
            try {
                // Extract to temp with original filename
                String filename = extractFilename(fileInfo.relativePath);
                Path extractedPath = extractor.extractFile(fileInfo.fileId, filename);

                if (extractedPath == null) {
                    log.warn("Failed to extract: {}", fileInfo.relativePath);
                    continue;
                }

                // Build VoicemailFile (without metadata yet)
                Path backupFilePath = extractor.getBackupFilePath(fileInfo.fileId);
                long fileSize = Files.size(extractedPath);

                VoicemailFile.Builder builder = new VoicemailFile.Builder()
                    .fileId(fileInfo.fileId)
                    .domain(fileInfo.domain)
                    .relativePath(fileInfo.relativePath)
                    .backupFilePath(backupFilePath)
                    .extractedPath(extractedPath)
                    .fileSize(fileSize);

                builders.add(builder);
                log.debug("Extracted: {} ({} bytes)", filename, fileSize);

            } catch (Exception e) {
                log.error("Error extracting {}: {}", fileInfo.relativePath, e.getMessage());
            }
        }

        log.info("Successfully extracted {} audio files", builders.size());
        return builders;
    }

    /**
     * Extract filename from relative path
     */
    private String extractFilename(String relativePath) {
        int lastSlash = relativePath.lastIndexOf('/');
        return lastSlash >= 0 ? relativePath.substring(lastSlash + 1) : relativePath;
    }
}
```

---

## Testing Extractor Module

**Test file:** `src/test/java/com/voicemail/extractor/ExtractorTest.java`

```java
package com.voicemail.extractor;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class ExtractorTest {

    @Test
    void testVoicemailFileBuilder() {
        VoicemailFile.VoicemailMetadata metadata = new VoicemailFile.VoicemailMetadata(
            1, 100, Instant.now(), "+1234567890", "+1234567890",
            45, null, null, 0
        );

        VoicemailFile file = new VoicemailFile.Builder()
            .fileId("abc123")
            .relativePath("voicemail/1699123456.amr")
            .backupFilePath(java.nio.file.Paths.get("/backup/ab/abc123"))
            .metadata(metadata)
            .build();

        assertNotNull(file);
        assertTrue(file.hasMetadata());
        assertEquals("1699123456.amr", file.getOriginalFilename());
        assertEquals(VoicemailFile.AudioFormat.AMR_NB, file.getFormat());
    }

    @Test
    void testAudioFormatDetection() {
        assertEquals(VoicemailFile.AudioFormat.AMR_NB,
            VoicemailFile.AudioFormat.fromExtension(".amr"));
        assertEquals(VoicemailFile.AudioFormat.AMR_WB,
            VoicemailFile.AudioFormat.fromExtension(".awb"));
        assertEquals(VoicemailFile.AudioFormat.AAC,
            VoicemailFile.AudioFormat.fromExtension(".m4a"));
    }

    @Test
    void testVoicemailMetadata() {
        VoicemailFile.VoicemailMetadata metadata = new VoicemailFile.VoicemailMetadata(
            1, 100, Instant.now(), "+1234567890", "+1234567890",
            45, null, null, 0x01  // Read flag
        );

        assertTrue(metadata.isRead());
        assertFalse(metadata.isSpam());
        assertFalse(metadata.wasTrashed());
    }

    // Full integration tests would require actual backup files
}
```

---

## Implementation Checklist

- [ ] Implement `VoicemailFile` with nested classes
- [ ] Implement `ManifestDbReader`
- [ ] Implement `VoicemailDbReader`
- [ ] Implement `FileExtractor`
- [ ] Implement `FileMatcher`
- [ ] Implement `VoicemailExtractor`
- [ ] Write unit tests
- [ ] Test with real iOS backup
- [ ] Test file matching logic
- [ ] Test synthetic metadata creation

---

## Usage Example

```java
// In main application
BackupInfo backup = ...; // from BackupDiscovery
TempDirectoryManager tempManager = new TempDirectoryManager();
tempManager.createTempDirectory();

VoicemailExtractor extractor = new VoicemailExtractor(backup, tempManager);
List<VoicemailFile> voicemails = extractor.extractVoicemails();

System.out.println("Extracted " + voicemails.size() + " voicemails");

for (VoicemailFile vm : voicemails) {
    System.out.println("  - " + vm.getOriginalFilename());
    if (vm.hasMetadata()) {
        System.out.println("    Caller: " + vm.getMetadata().getCallerNumber());
        System.out.println("    Duration: " + vm.getMetadata().getDurationSeconds() + "s");
    }
}
```

---

## Common Pitfalls

1. **SHA-1 Hash Calculation** - Must be exact: `domain + "-" + relativePath`
2. **File Path Construction** - Use first 2 chars of hash as directory
3. **Timestamp Matching** - Allow tolerance (±5 seconds)
4. **SQLite Connection** - Always close connections in finally blocks
5. **Null Metadata** - Handle cases where voicemail.db is missing
6. **File Permissions** - Check backup files are readable

---

**End of Extractor Module Implementation Guide**
