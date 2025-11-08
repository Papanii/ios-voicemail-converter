package com.voicemail.exception;

/**
 * Exception thrown when no voicemails are found in backup.
 * Exit code: 5
 */
public class NoVoicemailsException extends VoicemailConverterException {
    public NoVoicemailsException() {
        super(
            "No voicemails found in backup",
            5,
            "This could mean:\n" +
            "  - No voicemails were saved at time of backup\n" +
            "  - Voicemails were deleted before backup\n" +
            "  - Backup doesn't include voicemail data\n" +
            "\n" +
            "To fix:\n" +
            "  1. Ensure voicemails exist on device\n" +
            "  2. Create a new backup\n" +
            "  3. Run this tool again"
        );
    }

    public NoVoicemailsException(String additionalInfo) {
        super(
            "No voicemails found in backup: " + additionalInfo,
            5,
            "Check that voicemails exist on device before creating backup"
        );
    }
}
