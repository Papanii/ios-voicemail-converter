package com.voicemail.cli;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Immutable configuration object containing all parsed command-line arguments.
 * Use the Builder pattern to construct instances.
 */
public class Arguments {
    // Core paths
    private final Path backupDir;
    private final Path outputDir;

    // Backup selection
    private final String deviceId;      // nullable
    private final String backupPassword; // nullable

    // Output options
    private final String format;
    private final boolean keepOriginals;
    private final boolean includeMetadata;

    // Logging
    private final boolean verbose;
    private final Path logFile;         // nullable

    // Private constructor - force use of Builder
    private Arguments(Builder builder) {
        this.backupDir = builder.backupDir;
        this.outputDir = builder.outputDir;
        this.deviceId = builder.deviceId;
        this.backupPassword = builder.backupPassword;
        this.format = builder.format;
        this.keepOriginals = builder.keepOriginals;
        this.includeMetadata = builder.includeMetadata;
        this.verbose = builder.verbose;
        this.logFile = builder.logFile;
    }

    // Getters
    public Path getBackupDir() {
        return backupDir;
    }

    public Path getOutputDir() {
        return outputDir;
    }

    public Optional<String> getDeviceId() {
        return Optional.ofNullable(deviceId);
    }

    public Optional<String> getBackupPassword() {
        return Optional.ofNullable(backupPassword);
    }

    public String getFormat() {
        return format;
    }

    public boolean isKeepOriginals() {
        return keepOriginals;
    }

    public boolean isIncludeMetadata() {
        return includeMetadata;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public Optional<Path> getLogFile() {
        return Optional.ofNullable(logFile);
    }

    /**
     * Builder for Arguments
     */
    public static class Builder {
        private Path backupDir;
        private Path outputDir;
        private String deviceId;
        private String backupPassword;
        private String format = "wav";  // default
        private boolean keepOriginals = false;
        private boolean includeMetadata = false;
        private boolean verbose = false;
        private Path logFile;

        public Builder backupDir(Path backupDir) {
            this.backupDir = backupDir;
            return this;
        }

        public Builder outputDir(Path outputDir) {
            this.outputDir = outputDir;
            return this;
        }

        public Builder deviceId(String deviceId) {
            this.deviceId = deviceId;
            return this;
        }

        public Builder backupPassword(String backupPassword) {
            this.backupPassword = backupPassword;
            return this;
        }

        public Builder format(String format) {
            this.format = format;
            return this;
        }

        public Builder keepOriginals(boolean keepOriginals) {
            this.keepOriginals = keepOriginals;
            return this;
        }

        public Builder includeMetadata(boolean includeMetadata) {
            this.includeMetadata = includeMetadata;
            return this;
        }

        public Builder verbose(boolean verbose) {
            this.verbose = verbose;
            return this;
        }

        public Builder logFile(Path logFile) {
            this.logFile = logFile;
            return this;
        }

        /**
         * Build the Arguments object
         * @return Immutable Arguments instance
         * @throws IllegalStateException if required fields are missing
         */
        public Arguments build() {
            // Validate required fields
            if (backupDir == null) {
                throw new IllegalStateException("backupDir is required");
            }
            if (outputDir == null) {
                throw new IllegalStateException("outputDir is required");
            }

            return new Arguments(this);
        }
    }

    @Override
    public String toString() {
        return "Arguments{" +
                "backupDir=" + backupDir +
                ", outputDir=" + outputDir +
                ", deviceId=" + (deviceId != null ? "***" : "null") +
                ", backupPassword=" + (backupPassword != null ? "***" : "null") +
                ", format='" + format + '\'' +
                ", keepOriginals=" + keepOriginals +
                ", includeMetadata=" + includeMetadata +
                ", verbose=" + verbose +
                ", logFile=" + logFile +
                '}';
    }
}
