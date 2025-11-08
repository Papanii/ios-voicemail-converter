package com.voicemail.metadata;

import com.voicemail.extractor.VoicemailFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

/**
 * Main orchestrator for metadata processing
 */
public class MetadataProcessor {
    private static final Logger log = LoggerFactory.getLogger(MetadataProcessor.class);

    private final MetadataEmbedder embedder;
    private final JSONExporter jsonExporter;

    public MetadataProcessor() {
        this.embedder = new MetadataEmbedder();
        this.jsonExporter = new JSONExporter();
    }

    /**
     * Process metadata for a voicemail file
     */
    public ProcessedMetadata processMetadata(
            VoicemailFile voicemailFile,
            String deviceName,
            String iosVersion,
            Instant backupDate) {

        log.debug("Processing metadata for: {}", voicemailFile.getOriginalFilename());

        // Build metadata map for WAV embedding
        Map<String, String> wavMetadata = embedder.buildMetadataMap(
            voicemailFile,
            deviceName
        );

        // Create processed metadata object
        ProcessedMetadata processed = new ProcessedMetadata(
            voicemailFile,
            wavMetadata,
            deviceName,
            iosVersion,
            backupDate
        );

        log.debug("Processed metadata: {}", embedder.formatMetadataForLogging(wavMetadata));
        return processed;
    }

    /**
     * Export metadata to JSON file
     */
    public void exportToJSON(
            ProcessedMetadata metadata,
            Path outputPath) throws IOException {

        log.info("Exporting metadata to JSON: {}", outputPath);

        jsonExporter.exportMetadata(
            metadata.getVoicemailFile(),
            outputPath,
            metadata.getDeviceName(),
            metadata.getIosVersion(),
            metadata.getBackupDate()
        );
    }

    /**
     * Generate FFmpeg metadata arguments
     */
    public String[] generateFFmpegArgs(ProcessedMetadata metadata) {
        return embedder.generateFFmpegMetadataArgs(metadata.getWavMetadata());
    }

    /**
     * Processed metadata container
     */
    public static class ProcessedMetadata {
        private final VoicemailFile voicemailFile;
        private final Map<String, String> wavMetadata;
        private final String deviceName;
        private final String iosVersion;
        private final Instant backupDate;

        public ProcessedMetadata(
                VoicemailFile voicemailFile,
                Map<String, String> wavMetadata,
                String deviceName,
                String iosVersion,
                Instant backupDate) {

            this.voicemailFile = voicemailFile;
            this.wavMetadata = wavMetadata;
            this.deviceName = deviceName;
            this.iosVersion = iosVersion;
            this.backupDate = backupDate;
        }

        public VoicemailFile getVoicemailFile() {
            return voicemailFile;
        }

        public Map<String, String> getWavMetadata() {
            return wavMetadata;
        }

        public String getDeviceName() {
            return deviceName;
        }

        public String getIosVersion() {
            return iosVersion;
        }

        public Instant getBackupDate() {
            return backupDate;
        }
    }
}
