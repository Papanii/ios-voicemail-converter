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
     * Extract voicemail.db to temp directory using ManifestDbReader
     */
    public Path extractVoicemailDb(ManifestDbReader manifestReader) throws Exception {
        log.info("Extracting voicemail.db");

        // Query voicemail.db from Manifest
        ManifestDbReader.FileInfo voicemailDbInfo = manifestReader.queryVoicemailDbFile();

        if (voicemailDbInfo == null) {
            log.warn("voicemail.db not found in Manifest.db");
            // List all voicemail-related files for debugging
            manifestReader.listLibraryVoicemailFiles();
            return null;
        }

        // Extract using the fileID from Manifest
        Path extracted = extractFile(voicemailDbInfo.fileId, "voicemail.db");

        if (extracted == null) {
            log.warn("voicemail.db file not found in backup directory");
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
