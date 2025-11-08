package com.voicemail.exception;

import java.nio.file.Path;

/**
 * Exception thrown when audio conversion fails.
 * Exit code: 1
 */
public class ConversionException extends VoicemailConverterException {
    private final Path inputFile;
    private final String ffmpegError;

    public ConversionException(Path inputFile, String message) {
        super(message, 1);
        this.inputFile = inputFile;
        this.ffmpegError = null;
    }

    public ConversionException(Path inputFile, String message, String ffmpegError) {
        super(message, 1, buildSuggestion(ffmpegError));
        this.inputFile = inputFile;
        this.ffmpegError = ffmpegError;
    }

    public ConversionException(Path inputFile, String message, Throwable cause) {
        super(message, 1, null, cause);
        this.inputFile = inputFile;
        this.ffmpegError = null;
    }

    public Path getInputFile() {
        return inputFile;
    }

    public String getFfmpegError() {
        return ffmpegError;
    }

    public boolean hasFfmpegError() {
        return ffmpegError != null && !ffmpegError.isEmpty();
    }

    private static String buildSuggestion(String ffmpegError) {
        if (ffmpegError == null || ffmpegError.isEmpty()) {
            return "Check that the input file is a valid audio file";
        }

        if (ffmpegError.contains("Invalid data")) {
            return "Input file appears to be corrupted";
        } else if (ffmpegError.contains("No such file")) {
            return "Input file not found";
        } else if (ffmpegError.contains("Permission denied")) {
            return "Cannot read input file due to permissions";
        } else {
            return "Check FFmpeg error details in log file";
        }
    }
}
