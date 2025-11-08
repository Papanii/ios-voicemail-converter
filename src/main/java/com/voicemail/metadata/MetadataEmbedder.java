package com.voicemail.metadata;

import com.voicemail.extractor.VoicemailFile;
import com.voicemail.util.FormatUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
