package com.voicemail.extractor;

import com.voicemail.backup.BackupInfo;
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
