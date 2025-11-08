package com.voicemail.exception;

import java.nio.file.Path;

/**
 * Exception thrown when file system permissions prevent operation.
 * Exit code: 7
 */
public class PermissionException extends VoicemailConverterException {
    private final Path deniedPath;
    private final PermissionType permissionType;

    public enum PermissionType {
        READ("read from"),
        WRITE("write to"),
        EXECUTE("execute");

        private final String description;

        PermissionType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    public PermissionException(Path deniedPath, PermissionType permissionType) {
        super(
            "Permission denied: Cannot " + permissionType.getDescription() + " " + deniedPath,
            7,
            "Check file permissions or run with appropriate privileges"
        );
        this.deniedPath = deniedPath;
        this.permissionType = permissionType;
    }

    public PermissionException(Path deniedPath, PermissionType permissionType, String customSuggestion) {
        super(
            "Permission denied: Cannot " + permissionType.getDescription() + " " + deniedPath,
            7,
            customSuggestion
        );
        this.deniedPath = deniedPath;
        this.permissionType = permissionType;
    }

    public Path getDeniedPath() {
        return deniedPath;
    }

    public PermissionType getPermissionType() {
        return permissionType;
    }
}
