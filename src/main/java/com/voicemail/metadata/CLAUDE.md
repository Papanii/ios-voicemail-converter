# Metadata Module Implementation Guide

**Module:** Metadata Processing
**Package:** `com.voicemail.metadata`
**Status:** Not Implemented
**Priority:** Medium (Enhancement layer)

---

## Overview

The Metadata module processes voicemail metadata from the extractor, formats phone numbers, and handles metadata embedding in WAV files and JSON export.

## Module Purpose

Handle:
1. Phone number formatting and normalization
2. Metadata enrichment
3. WAV metadata embedding (INFO chunks)
4. JSON metadata export

---

## Dependencies

### External Libraries
- None (uses standard Java libraries)

### Internal Dependencies
- `com.voicemail.extractor.VoicemailFile` - Voicemail file data
- `com.voicemail.util.ValidationUtil` - Phone validation
- `com.voicemail.util.FormatUtil` - Formatting utilities

---

## Implementation Order

1. **PhoneNumberFormatter** - Format and normalize phone numbers (first)
2. **MetadataEmbedder** - Embed metadata in WAV files
3. **JSONExporter** - Export metadata as JSON
4. **MetadataProcessor** - Main orchestrator (last)

---

## Class 1: PhoneNumberFormatter

**Location:** `src/main/java/com/voicemail/metadata/PhoneNumberFormatter.java`

### Implementation

```java
package com.voicemail.metadata;

import java.util.regex.Pattern;

/**
 * Formats and normalizes phone numbers
 */
public class PhoneNumberFormatter {

    // Pattern for extracting digits
    private static final Pattern DIGITS_ONLY = Pattern.compile("[^0-9+]");

    // E.164 pattern
    private static final Pattern E164_PATTERN = Pattern.compile("^\\+[1-9]\\d{1,14}$");

    /**
     * Normalize phone number to E.164 format if possible
     */
    public static String normalizePhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            return "Unknown";
        }

        if ("Unknown".equalsIgnoreCase(phoneNumber)) {
            return "Unknown";
        }

        // Remove all non-digit characters except +
        String cleaned = phoneNumber.replaceAll("[^0-9+]", "");

        if (cleaned.isEmpty()) {
            return "Unknown";
        }

        // Already in E.164 format
        if (cleaned.startsWith("+")) {
            // Ensure + is only at start
            cleaned = "+" + cleaned.replaceAll("\\+", "");
            return cleaned;
        }

        // Try to add US country code
        if (cleaned.length() == 10) {
            // Assume US number
            return "+1" + cleaned;
        }

        if (cleaned.length() == 11 && cleaned.startsWith("1")) {
            // US number with country code, add +
            return "+" + cleaned;
        }

        // Return as-is with + prefix
        return "+" + cleaned;
    }

    /**
     * Format phone number for display (E.164 -> formatted)
     */
    public static String formatPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            return "Unknown";
        }

        if ("Unknown".equalsIgnoreCase(phoneNumber)) {
            return "Unknown";
        }

        // US format: +1-234-567-8900
        if (phoneNumber.startsWith("+1") && phoneNumber.length() == 12) {
            return phoneNumber.substring(0, 2) + "-" +
                   phoneNumber.substring(2, 5) + "-" +
                   phoneNumber.substring(5, 8) + "-" +
                   phoneNumber.substring(8);
        }

        // International format: +XX-XXX-XXX-XXXX (generic)
        if (phoneNumber.startsWith("+") && phoneNumber.length() > 5) {
            String countryCode = phoneNumber.substring(0, 3);
            String rest = phoneNumber.substring(3);

            // Insert dashes every 3-4 digits
            if (rest.length() > 6) {
                return countryCode + "-" +
                       rest.substring(0, 3) + "-" +
                       rest.substring(3, 6) + "-" +
                       rest.substring(6);
            }
        }

        return phoneNumber;
    }

    /**
     * Format phone number for filename (safe for filesystem)
     */
    public static String formatForFilename(String phoneNumber) {
        String normalized = normalizePhoneNumber(phoneNumber);

        if ("Unknown".equals(normalized)) {
            return "Unknown";
        }

        // Remove all non-alphanumeric except +
        String safe = normalized.replaceAll("[^0-9+]", "");

        // Limit length
        if (safe.length() > 20) {
            safe = safe.substring(0, 20);
        }

        return safe;
    }

    /**
     * Get caller display name (phone number or Unknown)
     */
    public static String getCallerDisplayName(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            return "Unknown";
        }

        if ("Unknown".equalsIgnoreCase(phoneNumber)) {
            return "Unknown";
        }

        return formatPhoneNumber(phoneNumber);
    }

    /**
     * Check if phone number is valid
     */
    public static boolean isValidPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            return false;
        }

        if ("Unknown".equalsIgnoreCase(phoneNumber)) {
            return false;
        }

        String normalized = normalizePhoneNumber(phoneNumber);
        return E164_PATTERN.matcher(normalized).matches();
    }
}
```

---

## Class 2: MetadataEmbedder

**Location:** `src/main/java/com/voicemail/metadata/MetadataEmbedder.java`

### Implementation

```java
package com.voicemail.metadata;

import com.voicemail.extractor.VoicemailFile;
import com.voicemail.util.FormatUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Embeds metadata into WAV files using FFmpeg
 */
public class MetadataEmbedder {
    private static final Logger log = LoggerFactory.getLogger(MetadataEmbedder.class);

    private static final String VERSION = "1.0.0";
    private static final DateTimeFormatter DATE_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * Build metadata map for FFmpeg
     */
    public Map<String, String> buildMetadataMap(VoicemailFile voicemailFile, String deviceName) {
        Map<String, String> metadata = new HashMap<>();

        if (!voicemailFile.hasMetadata()) {
            log.debug("No metadata available for {}", voicemailFile.getOriginalFilename());
            return metadata;
        }

        VoicemailFile.VoicemailMetadata vm = voicemailFile.getMetadata();

        // Title: Caller name or number
        String callerDisplay = PhoneNumberFormatter.getCallerDisplayName(
            vm.getCallerNumber()
        );
        metadata.put("title", callerDisplay);

        // Artist: Phone number (normalized)
        String phoneNormalized = PhoneNumberFormatter.normalizePhoneNumber(
            vm.getCallerNumber()
        );
        metadata.put("artist", phoneNormalized);

        // Date: Received date
        if (vm.getReceivedDate() != null) {
            LocalDateTime ldt = LocalDateTime.ofInstant(
                vm.getReceivedDate(),
                ZoneId.systemDefault()
            );
            metadata.put("date", ldt.format(DATE_FORMAT));
        }

        // Comment: Comprehensive metadata
        String comment = buildCommentString(vm, deviceName);
        metadata.put("comment", comment);

        // Encoded by: Tool information
        metadata.put("encoded_by", "iOS Voicemail Converter v" + VERSION);

        log.debug("Built metadata map with {} entries", metadata.size());
        return metadata;
    }

    /**
     * Build comprehensive comment string
     */
    private String buildCommentString(
            VoicemailFile.VoicemailMetadata metadata,
            String deviceName) {

        StringBuilder comment = new StringBuilder();

        // Duration
        comment.append("Duration: ").append(metadata.getDurationSeconds()).append("s");

        // Received date
        if (metadata.getReceivedDate() != null) {
            String dateStr = FormatUtil.formatTimestampForDisplay(
                metadata.getReceivedDate()
            );
            comment.append(", Received: ").append(dateStr);
        }

        // Device
        if (deviceName != null) {
            comment.append(", Device: ").append(deviceName);
        }

        // Status flags
        if (metadata.isSpam()) {
            comment.append(" [SPAM]");
        }
        if (metadata.isRead()) {
            comment.append(" [Read]");
        }

        return comment.toString();
    }

    /**
     * Generate FFmpeg metadata arguments
     */
    public String[] generateFFmpegMetadataArgs(Map<String, String> metadata) {
        if (metadata.isEmpty()) {
            return new String[0];
        }

        String[] args = new String[metadata.size() * 2];
        int i = 0;

        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            args[i++] = "-metadata";
            args[i++] = entry.getKey() + "=" + entry.getValue();
        }

        return args;
    }

    /**
     * Build metadata string for logging
     */
    public String formatMetadataForLogging(Map<String, String> metadata) {
        if (metadata.isEmpty()) {
            return "No metadata";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Metadata: ");

        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            sb.append(entry.getKey()).append("=")
              .append(entry.getValue()).append(", ");
        }

        // Remove trailing comma
        if (sb.length() > 10) {
            sb.setLength(sb.length() - 2);
        }

        return sb.toString();
    }
}
```

---

## Class 3: JSONExporter

**Location:** `src/main/java/com/voicemail/metadata/JSONExporter.java`

### Implementation

```java
package com.voicemail.metadata;

import com.voicemail.extractor.VoicemailFile;
import com.voicemail.util.FormatUtil;
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
```

---

## Class 4: MetadataProcessor

**Location:** `src/main/java/com/voicemail/metadata/MetadataProcessor.java`

### Implementation

```java
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
```

---

## Testing Metadata Module

**Test file:** `src/test/java/com/voicemail/metadata/MetadataTest.java`

```java
package com.voicemail.metadata;

import com.voicemail.extractor.VoicemailFile;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MetadataTest {

    @Test
    void testPhoneNormalization() {
        assertEquals("+12345678900", PhoneNumberFormatter.normalizePhoneNumber("(234) 567-8900"));
        assertEquals("+12345678900", PhoneNumberFormatter.normalizePhoneNumber("+1-234-567-8900"));
        assertEquals("+12345678900", PhoneNumberFormatter.normalizePhoneNumber("2345678900"));
        assertEquals("Unknown", PhoneNumberFormatter.normalizePhoneNumber("Unknown"));
        assertEquals("Unknown", PhoneNumberFormatter.normalizePhoneNumber(""));
    }

    @Test
    void testPhoneFormatting() {
        assertEquals("+1-234-567-8900", PhoneNumberFormatter.formatPhoneNumber("+12345678900"));
        assertEquals("Unknown", PhoneNumberFormatter.formatPhoneNumber("Unknown"));
    }

    @Test
    void testFilenameFormatting() {
        assertEquals("+12345678900", PhoneNumberFormatter.formatForFilename("(234) 567-8900"));
        assertEquals("Unknown", PhoneNumberFormatter.formatForFilename("Unknown"));
    }

    @Test
    void testMetadataEmbedder() {
        VoicemailFile.VoicemailMetadata metadata = new VoicemailFile.VoicemailMetadata(
            1, 100, Instant.now(), "+12345678900", "+12345678900",
            45, null, null, 0
        );

        VoicemailFile file = new VoicemailFile.Builder()
            .fileId("abc123")
            .relativePath("voicemail/test.amr")
            .backupFilePath(java.nio.file.Paths.get("/backup/ab/abc123"))
            .metadata(metadata)
            .build();

        MetadataEmbedder embedder = new MetadataEmbedder();
        Map<String, String> wavMetadata = embedder.buildMetadataMap(file, "Test iPhone");

        assertNotNull(wavMetadata);
        assertTrue(wavMetadata.containsKey("title"));
        assertTrue(wavMetadata.containsKey("artist"));
        assertTrue(wavMetadata.containsKey("comment"));
    }

    @Test
    void testFFmpegArgsGeneration() {
        Map<String, String> metadata = Map.of(
            "title", "John Doe",
            "artist", "+12345678900"
        );

        MetadataEmbedder embedder = new MetadataEmbedder();
        String[] args = embedder.generateFFmpegMetadataArgs(metadata);

        assertEquals(4, args.length);
        assertTrue(args[0].equals("-metadata"));
    }
}
```

---

## Implementation Checklist

- [ ] Implement `PhoneNumberFormatter`
- [ ] Implement `MetadataEmbedder`
- [ ] Implement `JSONExporter`
- [ ] Implement `MetadataProcessor`
- [ ] Write unit tests
- [ ] Test phone formatting with various inputs
- [ ] Test JSON export format
- [ ] Test metadata embedding

---

## Usage Example

```java
// In converter workflow
MetadataProcessor processor = new MetadataProcessor();

for (VoicemailFile vmFile : voicemails) {
    // Process metadata
    MetadataProcessor.ProcessedMetadata metadata = processor.processMetadata(
        vmFile,
        backup.getDeviceName(),
        backup.getProductVersion(),
        backup.getLastBackupDate().toInstant()
    );

    // Get FFmpeg args for embedding
    String[] ffmpegMetadataArgs = processor.generateFFmpegArgs(metadata);

    // Convert with metadata...

    // Export JSON if requested
    if (includeMetadata) {
        Path jsonPath = outputPath.resolveSibling(
            outputPath.getFileName().toString().replace(".wav", ".json")
        );
        processor.exportToJSON(metadata, jsonPath);
    }
}
```

---

**End of Metadata Module Implementation Guide**
