package com.voicemail.output;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Result of organizing converted voicemail files to output directories.
 * <p>
 * This immutable class contains information about successfully organized files,
 * failures, and overall statistics.
 * </p>
 */
public class OutputResult {
    private final int totalFiles;
    private final int successfulFiles;
    private final int failedFiles;
    private final List<OrganizedFile> organizedFiles;
    private final List<FileError> errors;
    private final Duration duration;

    /**
     * Information about a successfully organized file.
     */
    public static class OrganizedFile {
        private final Path wavFile;
        private final Path jsonFile;
        private final Path originalFile;  // null if --keep-originals not used
        private final String callerInfo;
        private final String receivedDate;

        public OrganizedFile(Path wavFile, Path jsonFile, Path originalFile,
                           String callerInfo, String receivedDate) {
            this.wavFile = Objects.requireNonNull(wavFile, "wavFile cannot be null");
            this.jsonFile = Objects.requireNonNull(jsonFile, "jsonFile cannot be null");
            this.originalFile = originalFile;  // Nullable
            this.callerInfo = callerInfo;
            this.receivedDate = receivedDate;
        }

        public Path getWavFile() { return wavFile; }
        public Path getJsonFile() { return jsonFile; }
        public Path getOriginalFile() { return originalFile; }
        public String getCallerInfo() { return callerInfo; }
        public String getReceivedDate() { return receivedDate; }

        @Override
        public String toString() {
            return String.format("OrganizedFile[wav=%s, json=%s, original=%s, caller=%s]",
                wavFile.getFileName(), jsonFile.getFileName(),
                originalFile != null ? originalFile.getFileName() : "none",
                callerInfo);
        }
    }

    /**
     * Information about a file that failed to organize.
     */
    public static class FileError {
        private final Path sourceFile;
        private final String errorMessage;
        private final Exception exception;

        public FileError(Path sourceFile, String errorMessage, Exception exception) {
            this.sourceFile = Objects.requireNonNull(sourceFile, "sourceFile cannot be null");
            this.errorMessage = Objects.requireNonNull(errorMessage, "errorMessage cannot be null");
            this.exception = exception;  // Nullable
        }

        public Path getSourceFile() { return sourceFile; }
        public String getErrorMessage() { return errorMessage; }
        public Exception getException() { return exception; }

        @Override
        public String toString() {
            return String.format("FileError[file=%s, error=%s]",
                sourceFile.getFileName(), errorMessage);
        }
    }

    private OutputResult(Builder builder) {
        this.totalFiles = builder.totalFiles;
        this.successfulFiles = builder.successfulFiles;
        this.failedFiles = builder.failedFiles;
        this.organizedFiles = Collections.unmodifiableList(builder.organizedFiles);
        this.errors = Collections.unmodifiableList(builder.errors);
        this.duration = builder.duration;
    }

    public int getTotalFiles() { return totalFiles; }
    public int getSuccessfulFiles() { return successfulFiles; }
    public int getFailedFiles() { return failedFiles; }
    public List<OrganizedFile> getOrganizedFiles() { return organizedFiles; }
    public List<FileError> getErrors() { return errors; }
    public Duration getDuration() { return duration; }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public boolean allSucceeded() {
        return failedFiles == 0;
    }

    public static class Builder {
        private int totalFiles;
        private int successfulFiles;
        private int failedFiles;
        private List<OrganizedFile> organizedFiles = new ArrayList<>();
        private List<FileError> errors = new ArrayList<>();
        private Duration duration = Duration.ZERO;

        public Builder totalFiles(int totalFiles) {
            this.totalFiles = totalFiles;
            return this;
        }

        public Builder successfulFiles(int successfulFiles) {
            this.successfulFiles = successfulFiles;
            return this;
        }

        public Builder failedFiles(int failedFiles) {
            this.failedFiles = failedFiles;
            return this;
        }

        public Builder organizedFiles(List<OrganizedFile> organizedFiles) {
            this.organizedFiles = new ArrayList<>(organizedFiles);
            return this;
        }

        public Builder addOrganizedFile(OrganizedFile file) {
            this.organizedFiles.add(file);
            return this;
        }

        public Builder errors(List<FileError> errors) {
            this.errors = new ArrayList<>(errors);
            return this;
        }

        public Builder addError(FileError error) {
            this.errors.add(error);
            return this;
        }

        public Builder duration(Duration duration) {
            this.duration = duration;
            return this;
        }

        public OutputResult build() {
            return new OutputResult(this);
        }
    }

    @Override
    public String toString() {
        return String.format("OutputResult[total=%d, success=%d, failed=%d, duration=%s]",
            totalFiles, successfulFiles, failedFiles, duration);
    }
}
