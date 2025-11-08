package com.voicemail.converter;

import java.nio.file.Path;
import java.time.Duration;

/**
 * Result of an audio conversion operation
 */
public class ConversionResult {
    private final boolean success;
    private final Path inputFile;
    private final Path outputFile;
    private final Duration conversionTime;
    private final long inputSize;
    private final long outputSize;
    private final String errorMessage;
    private final AudioInfo audioInfo;

    /**
     * Audio information
     */
    public static class AudioInfo {
        private final String codec;
        private final int sampleRate;
        private final int channels;
        private final int bitRate;
        private final double durationSeconds;

        public AudioInfo(String codec, int sampleRate, int channels,
                        int bitRate, double durationSeconds) {
            this.codec = codec;
            this.sampleRate = sampleRate;
            this.channels = channels;
            this.bitRate = bitRate;
            this.durationSeconds = durationSeconds;
        }

        public String getCodec() { return codec; }
        public int getSampleRate() { return sampleRate; }
        public int getChannels() { return channels; }
        public int getBitRate() { return bitRate; }
        public double getDurationSeconds() { return durationSeconds; }

        @Override
        public String toString() {
            return String.format("%s, %d Hz, %d ch, %.1fs",
                codec, sampleRate, channels, durationSeconds);
        }
    }

    private ConversionResult(Builder builder) {
        this.success = builder.success;
        this.inputFile = builder.inputFile;
        this.outputFile = builder.outputFile;
        this.conversionTime = builder.conversionTime;
        this.inputSize = builder.inputSize;
        this.outputSize = builder.outputSize;
        this.errorMessage = builder.errorMessage;
        this.audioInfo = builder.audioInfo;
    }

    // Getters
    public boolean isSuccess() { return success; }
    public Path getInputFile() { return inputFile; }
    public Path getOutputFile() { return outputFile; }
    public Duration getConversionTime() { return conversionTime; }
    public long getInputSize() { return inputSize; }
    public long getOutputSize() { return outputSize; }
    public String getErrorMessage() { return errorMessage; }
    public AudioInfo getAudioInfo() { return audioInfo; }

    public boolean hasError() {
        return errorMessage != null && !errorMessage.isEmpty();
    }

    @Override
    public String toString() {
        if (success) {
            return String.format("Success: %s → %s (%s in %s)",
                inputFile.getFileName(),
                outputFile.getFileName(),
                audioInfo != null ? audioInfo.toString() : "unknown",
                conversionTime != null ? conversionTime.toMillis() + "ms" : "unknown"
            );
        } else {
            return String.format("Failed: %s - %s",
                inputFile.getFileName(),
                errorMessage
            );
        }
    }

    /**
     * Builder for ConversionResult
     */
    public static class Builder {
        private boolean success;
        private Path inputFile;
        private Path outputFile;
        private Duration conversionTime;
        private long inputSize;
        private long outputSize;
        private String errorMessage;
        private AudioInfo audioInfo;

        public Builder success(boolean success) {
            this.success = success;
            return this;
        }

        public Builder inputFile(Path inputFile) {
            this.inputFile = inputFile;
            return this;
        }

        public Builder outputFile(Path outputFile) {
            this.outputFile = outputFile;
            return this;
        }

        public Builder conversionTime(Duration conversionTime) {
            this.conversionTime = conversionTime;
            return this;
        }

        public Builder inputSize(long inputSize) {
            this.inputSize = inputSize;
            return this;
        }

        public Builder outputSize(long outputSize) {
            this.outputSize = outputSize;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public Builder audioInfo(AudioInfo audioInfo) {
            this.audioInfo = audioInfo;
            return this;
        }

        public ConversionResult build() {
            return new ConversionResult(this);
        }
    }
}
