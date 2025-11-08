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
