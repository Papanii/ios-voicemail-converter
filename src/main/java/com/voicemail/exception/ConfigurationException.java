package com.voicemail.exception;

/**
 * Exception thrown when command-line arguments are invalid or incomplete.
 * This is a checked exception to force proper error handling.
 */
public class ConfigurationException extends Exception {
    private final int exitCode;
    private final String suggestion;

    /**
     * Create exception with message
     * @param message Error message
     */
    public ConfigurationException(String message) {
        this(message, 2, null);
    }

    /**
     * Create exception with message and exit code
     * @param message Error message
     * @param exitCode Exit code for application
     */
    public ConfigurationException(String message, int exitCode) {
        this(message, exitCode, null);
    }

    /**
     * Create exception with message, exit code, and suggestion
     * @param message Error message
     * @param exitCode Exit code for application
     * @param suggestion Suggested fix for user
     */
    public ConfigurationException(String message, int exitCode, String suggestion) {
        super(message);
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
