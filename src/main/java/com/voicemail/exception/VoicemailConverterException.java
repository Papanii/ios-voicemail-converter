package com.voicemail.exception;

/**
 * Base exception for all voicemail converter errors.
 * Provides exit code and optional suggestion for recovery.
 */
public class VoicemailConverterException extends Exception {
    private final int exitCode;
    private final String suggestion;

    /**
     * Create exception with message and default exit code
     */
    public VoicemailConverterException(String message) {
        this(message, 1, null);
    }

    /**
     * Create exception with message and exit code
     */
    public VoicemailConverterException(String message, int exitCode) {
        this(message, exitCode, null);
    }

    /**
     * Create exception with message, exit code, and suggestion
     */
    public VoicemailConverterException(String message, int exitCode, String suggestion) {
        super(message);
        this.exitCode = exitCode;
        this.suggestion = suggestion;
    }

    /**
     * Create exception with message, exit code, suggestion, and cause
     */
    public VoicemailConverterException(String message, int exitCode, String suggestion, Throwable cause) {
        super(message, cause);
        this.exitCode = exitCode;
        this.suggestion = suggestion;
    }

    public int getExitCode() {
        return exitCode;
    }

    public String getSuggestion() {
        return suggestion;
    }

    public boolean hasSuggestion() {
        return suggestion != null && !suggestion.isEmpty();
    }
}
