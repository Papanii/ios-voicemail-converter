package com.voicemail.exception;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for exception classes and error handling.
 * <p>
 * Verifies that custom exceptions:
 * - Contain correct error messages
 * - Have proper exit codes
 * - Include helpful suggestions
 * - Chain causes correctly
 * </p>
 */
class ErrorHandlingTest {

    @Test
    void testBackupException_withMessage() {
        String message = "Backup not found";
        BackupException exception = new BackupException(message);

        assertEquals(message, exception.getMessage());
        assertEquals(3, exception.getExitCode());
        assertFalse(exception.hasSuggestion());
    }

    @Test
    void testBackupException_withMessageAndSuggestion() {
        String message = "Backup not found";
        String suggestion = "Check the backup directory path";
        BackupException exception = new BackupException(message, suggestion);

        assertEquals(message, exception.getMessage());
        assertEquals(suggestion, exception.getSuggestion());
        assertTrue(exception.hasSuggestion());
        assertEquals(3, exception.getExitCode());
    }

    @Test
    void testBackupException_withCause() {
        Exception cause = new RuntimeException("Root cause");
        BackupException exception = new BackupException("Backup failed", "Check backup directory", cause);

        assertEquals("Backup failed", exception.getMessage());
        assertEquals(cause, exception.getCause());
    }

    @Test
    void testConfigurationException_invalidArgument() {
        String message = "Invalid output directory";
        ConfigurationException exception = new ConfigurationException(message);

        assertEquals(message, exception.getMessage());
        assertEquals(1, exception.getExitCode());
    }

    @Test
    void testConfigurationException_withSuggestion() {
        String message = "Invalid backup directory";
        String suggestion = "Specify a valid directory path with --backup-dir";
        ConfigurationException exception = new ConfigurationException(message, 1, suggestion);

        assertEquals(message, exception.getMessage());
        assertEquals(suggestion, exception.getSuggestion());
        assertTrue(exception.hasSuggestion());
    }

    @Test
    void testConversionException_withPath() {
        Path inputFile = Paths.get("/tmp/test.amr");
        String message = "FFmpeg conversion failed";
        ConversionException exception = new ConversionException(inputFile, message);

        assertTrue(exception.getMessage().contains(message));
        assertTrue(exception.getMessage().contains("test.amr"));
        assertEquals(4, exception.getExitCode());
    }

    @Test
    void testConversionException_withPathAndCause() {
        Path inputFile = Paths.get("/tmp/test.amr");
        String message = "FFmpeg execution failed";
        Exception cause = new RuntimeException("Process error");
        ConversionException exception = new ConversionException(inputFile, message, cause);

        assertTrue(exception.getMessage().contains(message));
        assertEquals(cause, exception.getCause());
    }

    @Test
    void testDependencyException_ffmpegNotFound() {
        String dependency = "ffmpeg";
        String message = "FFmpeg not found in PATH";
        DependencyException exception = new DependencyException(dependency, message);

        assertTrue(exception.getMessage().contains("ffmpeg"));
        assertTrue(exception.getMessage().contains(message));
        assertEquals(5, exception.getExitCode());
        assertTrue(exception.hasSuggestion());
        assertTrue(exception.getSuggestion().contains("brew install ffmpeg") ||
                   exception.getSuggestion().contains("apt install ffmpeg"));
    }

    @Test
    void testNoVoicemailsException() {
        NoVoicemailsException exception = new NoVoicemailsException();

        assertNotNull(exception.getMessage());
        assertTrue(exception.getMessage().contains("No voicemails found"));
        assertEquals(2, exception.getExitCode());
    }

    @Test
    void testPermissionException_fileAccess() {
        Path deniedPath = Paths.get("/tmp/output");
        PermissionException exception = new PermissionException(
            deniedPath, PermissionException.PermissionType.WRITE);

        assertTrue(exception.getMessage().contains("output"));
        assertEquals(7, exception.getExitCode());
    }

    @Test
    void testPermissionException_withSuggestion() {
        Path deniedPath = Paths.get("/tmp/backup");
        String suggestion = "Check file permissions with: ls -la /path/to/file";
        PermissionException exception = new PermissionException(
            deniedPath, PermissionException.PermissionType.READ, suggestion);

        assertTrue(exception.getMessage().contains("backup"));
        assertEquals(suggestion, exception.getSuggestion());
        assertTrue(exception.hasSuggestion());
    }

    @Test
    void testPermissionException_getters() {
        Path deniedPath = Paths.get("/tmp/backup");
        PermissionException exception = new PermissionException(
            deniedPath, PermissionException.PermissionType.READ);

        assertEquals(deniedPath, exception.getDeniedPath());
        assertEquals(PermissionException.PermissionType.READ, exception.getPermissionType());
    }

    @Test
    void testInsufficientStorageException_withRequiredSpace() {
        long required = 1024 * 1024 * 500; // 500 MB
        long available = 1024 * 1024 * 100; // 100 MB
        Path location = Paths.get("/tmp/output");
        InsufficientStorageException exception = new InsufficientStorageException(location, required, available);

        assertNotNull(exception.getMessage());
        assertTrue(exception.getMessage().contains("500"));
        assertTrue(exception.getMessage().contains("100"));
        assertTrue(exception.getMessage().contains("MB"));
        assertEquals(8, exception.getExitCode());
    }

    @Test
    void testInsufficientStorageException_suggestion() {
        Path location = Paths.get("/tmp/output");
        InsufficientStorageException exception =
            new InsufficientStorageException(location, 1024 * 1024 * 500, 1024 * 1024 * 100);

        assertTrue(exception.hasSuggestion());
        assertNotNull(exception.getSuggestion());
    }

    @Test
    void testVoicemailConverterException_hierarchy() {
        // Verify runtime exceptions extend VoicemailConverterException
        assertTrue(new BackupException("test") instanceof VoicemailConverterException);
        assertTrue(new ConversionException(Paths.get("/tmp"), "test") instanceof VoicemailConverterException);
        assertTrue(new DependencyException("dep", "test") instanceof VoicemailConverterException);
        assertTrue(new NoVoicemailsException() instanceof VoicemailConverterException);
        assertTrue(new PermissionException(Paths.get("/tmp"), PermissionException.PermissionType.WRITE) instanceof VoicemailConverterException);
        assertTrue(new InsufficientStorageException(Paths.get("/tmp"), 100, 50) instanceof VoicemailConverterException);

        // ConfigurationException extends Exception (checked exception), not VoicemailConverterException
        assertTrue(new ConfigurationException("test") instanceof Exception);
    }

    @Test
    void testExitCodes_areUnique() {
        // Verify each exception type has a unique exit code
        int[] exitCodes = {
            new ConfigurationException("").getExitCode(),  // 1
            new NoVoicemailsException().getExitCode(),     // 2
            new BackupException("").getExitCode(),         // 3
            new ConversionException(Paths.get("/tmp"), "").getExitCode(), // 4
            new DependencyException("", "").getExitCode(), // 5
            new PermissionException(Paths.get("/tmp"), PermissionException.PermissionType.WRITE).getExitCode(),     // 7
            new InsufficientStorageException(Paths.get("/tmp"), 100, 50).getExitCode() // 8
        };

        // Check all are unique
        for (int i = 0; i < exitCodes.length; i++) {
            for (int j = i + 1; j < exitCodes.length; j++) {
                assertNotEquals(exitCodes[i], exitCodes[j],
                    "Exit codes must be unique");
            }
        }
    }

    @Test
    void testExitCodes_areNonZero() {
        // Verify all error exit codes are non-zero
        assertTrue(new BackupException("").getExitCode() != 0);
        assertTrue(new ConfigurationException("").getExitCode() != 0);
        assertTrue(new ConversionException(Paths.get("/tmp"), "").getExitCode() != 0);
        assertTrue(new DependencyException("", "").getExitCode() != 0);
        assertTrue(new NoVoicemailsException().getExitCode() != 0);
        assertTrue(new PermissionException(Paths.get("/tmp"), PermissionException.PermissionType.WRITE).getExitCode() != 0);
        assertTrue(new InsufficientStorageException(Paths.get("/tmp"), 100, 50).getExitCode() != 0);
    }

    @Test
    void testExceptionMessages_areNotEmpty() {
        // Verify all exceptions have non-empty messages
        assertFalse(new BackupException("test").getMessage().isEmpty());
        assertFalse(new ConfigurationException("test").getMessage().isEmpty());
        assertFalse(new ConversionException(Paths.get("/tmp"), "test").getMessage().isEmpty());
        assertFalse(new DependencyException("dep", "test").getMessage().isEmpty());
        assertFalse(new NoVoicemailsException().getMessage().isEmpty());
        assertFalse(new PermissionException(Paths.get("/tmp"), PermissionException.PermissionType.WRITE).getMessage().isEmpty());
        assertFalse(new InsufficientStorageException(Paths.get("/tmp"), 100, 50).getMessage().isEmpty());
    }
}
