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
