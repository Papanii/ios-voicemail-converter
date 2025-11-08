package com.voicemail.exception;

import java.nio.file.Path;

/**
 * Exception thrown when backup cannot be found or accessed.
 * Exit code: 3
 */
public class BackupException extends VoicemailConverterException {
    private final Path backupPath;

    public BackupException(String message) {
        super(message, 3);
        this.backupPath = null;
    }

    public BackupException(String message, int exitCode) {
        super(message, exitCode);
        this.backupPath = null;
    }

    public BackupException(String message, String suggestion) {
        super(message, 3, suggestion);
        this.backupPath = null;
    }

    public BackupException(String message, String suggestion, Throwable cause) {
        super(message, 3, suggestion, cause);
        this.backupPath = null;
    }

    public BackupException(Path backupPath, String message, String suggestion) {
        super(message, 3, suggestion);
        this.backupPath = backupPath;
    }

    public Path getBackupPath() {
        return backupPath;
    }

    public boolean hasBackupPath() {
        return backupPath != null;
    }
}
