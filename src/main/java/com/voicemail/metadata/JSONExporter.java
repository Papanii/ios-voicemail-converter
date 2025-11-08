package com.voicemail.metadata;

import com.voicemail.extractor.VoicemailFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/**
 * Exports voicemail metadata as JSON files
 */
public class JSONExporter {
    private static final Logger log = LoggerFactory.getLogger(JSONExporter.class);

    private static final String VERSION = "1.0.0";

    /**
     * Export metadata to JSON file
     */
    public void exportMetadata(
            VoicemailFile voicemailFile,
            Path outputPath,
            String deviceName,
            String iosVersion,
            Instant backupDate) throws IOException {

        log.debug("Exporting metadata to: {}", outputPath);

        String json = buildJSON(voicemailFile, deviceName, iosVersion, backupDate);
        Files.writeString(outputPath, json, StandardCharsets.UTF_8);

        log.debug("Exported {} bytes", json.length());
    }

    /**
     * Build JSON string
     */
    private String buildJSON(
            VoicemailFile file,
            String deviceName,
            String iosVersion,
            Instant backupDate) {

        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"voicemail\": {\n");

        // Caller information
        appendCallerInfo(json, file);

        // Timestamps
        appendTimestamps(json, file, backupDate);

        // Duration
        appendDuration(json, file);

        // Status
        appendStatus(json, file);

        // Audio information
        appendAudioInfo(json, file);

        // Device information
        appendDeviceInfo(json, deviceName, iosVersion);

        // Backup information
        appendBackupInfo(json, backupDate);

        // Conversion information
        appendConversionInfo(json);

        json.append("  }\n");
        json.append("}\n");

        return json.toString();
    }

    private void appendCallerInfo(StringBuilder json, VoicemailFile file) {
        json.append("    \"caller\": {\n");

        if (file.hasMetadata()) {
            VoicemailFile.VoicemailMetadata meta = file.getMetadata();

            String phoneNumber = PhoneNumberFormatter.normalizePhoneNumber(
                meta.getCallerNumber()
            );
            String displayName = PhoneNumberFormatter.getCallerDisplayName(
                meta.getCallerNumber()
            );

            json.append("      \"phoneNumber\": ").append(quote(phoneNumber)).append(",\n");
            json.append("      \"displayName\": ").append(quote(displayName)).append(",\n");

            if (meta.getCallbackNumber() != null) {
                String callback = PhoneNumberFormatter.normalizePhoneNumber(
                    meta.getCallbackNumber()
                );
                json.append("      \"callbackNumber\": ").append(quote(callback)).append("\n");
            } else {
                json.append("      \"callbackNumber\": null\n");
            }
        } else {
            json.append("      \"phoneNumber\": \"Unknown\",\n");
            json.append("      \"displayName\": \"Unknown\",\n");
            json.append("      \"callbackNumber\": null\n");
        }

        json.append("    },\n");
    }

    private void appendTimestamps(StringBuilder json, VoicemailFile file, Instant backupDate) {
        json.append("    \"timestamps\": {\n");

        if (file.hasMetadata() && file.getMetadata().getReceivedDate() != null) {
            json.append("      \"received\": ").append(
                quote(file.getMetadata().getReceivedDate().toString())
            ).append(",\n");

            if (file.getMetadata().getExpirationDate() != null) {
                json.append("      \"expiration\": ").append(
                    quote(file.getMetadata().getExpirationDate().toString())
                ).append(",\n");
            } else {
                json.append("      \"expiration\": null,\n");
            }
        } else {
            json.append("      \"received\": null,\n");
            json.append("      \"expiration\": null,\n");
        }

        json.append("      \"extracted\": ").append(quote(Instant.now().toString())).append("\n");
        json.append("    },\n");
    }

    private void appendDuration(StringBuilder json, VoicemailFile file) {
        json.append("    \"duration\": {\n");

        if (file.hasMetadata()) {
            json.append("      \"databaseSeconds\": ")
                .append(file.getMetadata().getDurationSeconds()).append(",\n");
        } else {
            json.append("      \"databaseSeconds\": 0,\n");
        }

        json.append("      \"actualMilliseconds\": 0\n");  // Will be filled by converter
        json.append("    },\n");
    }

    private void appendStatus(StringBuilder json, VoicemailFile file) {
        json.append("    \"status\": {\n");

        if (file.hasMetadata()) {
            VoicemailFile.VoicemailMetadata meta = file.getMetadata();
            json.append("      \"isRead\": ").append(meta.isRead()).append(",\n");
            json.append("      \"isSpam\": ").append(meta.isSpam()).append(",\n");
            json.append("      \"wasDeleted\": ").append(meta.wasTrashed()).append("\n");
        } else {
            json.append("      \"isRead\": false,\n");
            json.append("      \"isSpam\": false,\n");
            json.append("      \"wasDeleted\": false\n");
        }

        json.append("    },\n");
    }

    private void appendAudioInfo(StringBuilder json, VoicemailFile file) {
        json.append("    \"audio\": {\n");
        json.append("      \"originalFilename\": ").append(
            quote(file.getOriginalFilename())
        ).append(",\n");
        json.append("      \"originalFormat\": ").append(
            quote(file.getFormat().getDescription())
        ).append(",\n");
        json.append("      \"originalSizeBytes\": ").append(file.getFileSize()).append(",\n");
        json.append("      \"convertedFormat\": \"WAV\",\n");
        json.append("      \"sampleRate\": 0,\n");  // Will be filled by converter
        json.append("      \"bitRate\": 0,\n");
        json.append("      \"channels\": 1\n");
        json.append("    },\n");
    }

    private void appendDeviceInfo(StringBuilder json, String deviceName, String iosVersion) {
        json.append("    \"device\": {\n");
        json.append("      \"name\": ").append(quote(deviceName)).append(",\n");
        json.append("      \"model\": null,\n");  // Not available at this stage
        json.append("      \"iosVersion\": ").append(quote(iosVersion)).append("\n");
        json.append("    },\n");
    }

    private void appendBackupInfo(StringBuilder json, Instant backupDate) {
        json.append("    \"backup\": {\n");
        json.append("      \"date\": ").append(
            backupDate != null ? quote(backupDate.toString()) : "null"
        ).append(",\n");
        json.append("      \"path\": null\n");  // Not including full path for privacy
        json.append("    },\n");
    }

    private void appendConversionInfo(StringBuilder json) {
        json.append("    \"conversion\": {\n");
        json.append("      \"toolVersion\": ").append(quote(VERSION)).append(",\n");
        json.append("      \"date\": ").append(quote(Instant.now().toString())).append(",\n");
        json.append("      \"outputFilename\": null\n");  // Will be set by organizer
        json.append("    }\n");
    }

    /**
     * Quote string for JSON
     */
    private String quote(String str) {
        if (str == null) {
            return "null";
        }
        // Escape special characters
        String escaped = str
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
        return "\"" + escaped + "\"";
    }
}
