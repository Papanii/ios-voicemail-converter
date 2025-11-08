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
