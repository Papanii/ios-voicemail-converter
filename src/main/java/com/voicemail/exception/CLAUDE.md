# Exception Module Implementation Guide

**Module:** Exception Handling
**Package:** `com.voicemail.exception`
**Status:** Partially Implemented (ConfigurationException done in CLI module)
**Priority:** High (Foundation for error handling)

---

## Overview

The Exception module provides custom exception classes for the entire application. These exceptions provide structured error handling with exit codes and user-friendly messages.

## Module Purpose

Define custom exceptions for:
1. Configuration errors (CLI arguments)
2. Backup-related errors
3. Extraction errors
4. Conversion errors
5. Permission/IO errors
6. Insufficient resources

---

## Exception Hierarchy

```
Exception (java.lang)
│
├── VoicemailConverterException (base)
│   ├── ConfigurationException (exit code 2) ✅ Already implemented
│   ├── DependencyException (exit code 6)
│   ├── BackupException (exit code 3)
│   │   └── EncryptionException (exit code 4)
│   ├── NoVoicemailsException (exit code 5)
│   ├── PermissionException (exit code 7)
│   ├── InsufficientStorageException (exit code 8)
│   └── ConversionException (exit code 1)
```

---

## Implementation Order

1. **VoicemailConverterException** - Base class (first)
2. **DependencyException** - Missing FFmpeg
3. **BackupException** - Backup not found/invalid
4. **EncryptionException** - Extends BackupException
5. **NoVoicemailsException** - No data to process
6. **PermissionException** - File access denied
7. **InsufficientStorageException** - Disk space
8. **ConversionException** - Audio conversion errors

---

## Class 1: VoicemailConverterException (Base)

**Location:** `src/main/java/com/voicemail/exception/VoicemailConverterException.java`

### Implementation

```java
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
```

---

## Class 2: DependencyException

**Location:** `src/main/java/com/voicemail/exception/DependencyException.java`

### Implementation

```java
package com.voicemail.exception;

/**
 * Exception thrown when required dependencies are missing.
 * Exit code: 6
 */
public class DependencyException extends VoicemailConverterException {
    private final String dependency;

    public DependencyException(String dependency, String message) {
        super(message, 6, buildSuggestion(dependency));
        this.dependency = dependency;
    }

    public DependencyException(String dependency, String message, Throwable cause) {
        super(message, 6, buildSuggestion(dependency), cause);
        this.dependency = dependency;
    }

    public String getDependency() {
        return dependency;
    }

    private static String buildSuggestion(String dependency) {
        switch (dependency.toLowerCase()) {
            case "ffmpeg":
                return "Install FFmpeg:\n" +
                       "  macOS:    brew install ffmpeg\n" +
                       "  Ubuntu:   sudo apt install ffmpeg\n" +
                       "  Windows:  Download from https://ffmpeg.org/download.html";
            case "ffprobe":
                return "FFprobe is included with FFmpeg. Install FFmpeg.";
            default:
                return "Install " + dependency + " and ensure it's in your PATH";
        }
    }
}
```

---

## Class 3: BackupException

**Location:** `src/main/java/com/voicemail/exception/BackupException.java`

### Implementation

```java
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

    public BackupException(String message, String suggestion) {
        super(message, 3, suggestion);
        this.backupPath = null;
    }

    public BackupException(Path backupPath, String message, String suggestion) {
        super(message, 3, suggestion);
        this.backupPath = backupPath;
    }

    public BackupException(String message, Throwable cause) {
        super(message, 3, null, cause);
        this.backupPath = null;
    }

    public Path getBackupPath() {
        return backupPath;
    }

    public boolean hasBackupPath() {
        return backupPath != null;
    }
}
```

---

## Class 4: EncryptionException

**Location:** `src/main/java/com/voicemail/exception/EncryptionException.java`

### Implementation

```java
package com.voicemail.exception;

/**
 * Exception thrown when backup is encrypted and cannot be accessed.
 * Exit code: 4
 */
public class EncryptionException extends BackupException {
    private final boolean passwordProvided;

    public EncryptionException(boolean passwordProvided) {
        super(
            buildMessage(passwordProvided),
            buildSuggestion(passwordProvided)
        );
        this.passwordProvided = passwordProvided;
    }

    public boolean wasPasswordProvided() {
        return passwordProvided;
    }

    private static String buildMessage(boolean passwordProvided) {
        if (passwordProvided) {
            return "Backup is encrypted and the provided password is incorrect";
        } else {
            return "Backup is encrypted and requires a password";
        }
    }

    private static String buildSuggestion(boolean passwordProvided) {
        if (passwordProvided) {
            return "Check the password and try again, or create an unencrypted backup";
        } else {
            return "Provide password with --backup-password, or:\n" +
                   "  1. Open Finder (macOS) or iTunes (Windows)\n" +
                   "  2. Connect your iPhone\n" +
                   "  3. Disable 'Encrypt local backup'\n" +
                   "  4. Create a new backup\n" +
                   "  5. Run this tool again";
        }
    }

    @Override
    public int getExitCode() {
        return 4;
    }
}
```

---

## Class 5: NoVoicemailsException

**Location:** `src/main/java/com/voicemail/exception/NoVoicemailsException.java`

### Implementation

```java
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
```

---

## Class 6: PermissionException

**Location:** `src/main/java/com/voicemail/exception/PermissionException.java`

### Implementation

```java
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
```

---

## Class 7: InsufficientStorageException

**Location:** `src/main/java/com/voicemail/exception/InsufficientStorageException.java`

### Implementation

```java
package com.voicemail.exception;

import java.nio.file.Path;

/**
 * Exception thrown when insufficient disk space is available.
 * Exit code: 8
 */
public class InsufficientStorageException extends VoicemailConverterException {
    private final Path location;
    private final long requiredBytes;
    private final long availableBytes;

    public InsufficientStorageException(Path location, long requiredBytes, long availableBytes) {
        super(
            buildMessage(location, requiredBytes, availableBytes),
            8,
            buildSuggestion(location, requiredBytes, availableBytes)
        );
        this.location = location;
        this.requiredBytes = requiredBytes;
        this.availableBytes = availableBytes;
    }

    public Path getLocation() {
        return location;
    }

    public long getRequiredBytes() {
        return requiredBytes;
    }

    public long getAvailableBytes() {
        return availableBytes;
    }

    private static String buildMessage(Path location, long required, long available) {
        return String.format(
            "Insufficient disk space at %s: Need %s, have %s",
            location,
            formatBytes(required),
            formatBytes(available)
        );
    }

    private static String buildSuggestion(Path location, long required, long available) {
        long shortage = required - available;
        return String.format(
            "Free up at least %s of disk space, or:\n" +
            "  - Use a different output directory with --output-dir\n" +
            "  - Choose a location with more available space",
            formatBytes(shortage)
        );
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024));
        } else {
            return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
        }
    }
}
```

---

## Class 8: ConversionException

**Location:** `src/main/java/com/voicemail/exception/ConversionException.java`

### Implementation

```java
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
```

---

## Testing All Exceptions

**Test file:** `src/test/java/com/voicemail/exception/ExceptionTest.java`

```java
package com.voicemail.exception;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

class ExceptionTest {

    @Test
    void testVoicemailConverterException() {
        VoicemailConverterException ex = new VoicemailConverterException("Test error");
        assertEquals("Test error", ex.getMessage());
        assertEquals(1, ex.getExitCode());
        assertFalse(ex.hasSuggestion());
    }

    @Test
    void testVoicemailConverterExceptionWithSuggestion() {
        VoicemailConverterException ex = new VoicemailConverterException(
            "Test error", 5, "Try this"
        );
        assertTrue(ex.hasSuggestion());
        assertEquals("Try this", ex.getSuggestion());
    }

    @Test
    void testDependencyException() {
        DependencyException ex = new DependencyException("ffmpeg", "FFmpeg not found");
        assertEquals(6, ex.getExitCode());
        assertEquals("ffmpeg", ex.getDependency());
        assertTrue(ex.hasSuggestion());
        assertTrue(ex.getSuggestion().contains("brew install ffmpeg"));
    }

    @Test
    void testBackupException() {
        Path path = Paths.get("/backup");
        BackupException ex = new BackupException(path, "Backup not found", "Create backup");
        assertEquals(3, ex.getExitCode());
        assertTrue(ex.hasBackupPath());
        assertEquals(path, ex.getBackupPath());
    }

    @Test
    void testEncryptionExceptionNoPassword() {
        EncryptionException ex = new EncryptionException(false);
        assertEquals(4, ex.getExitCode());
        assertFalse(ex.wasPasswordProvided());
        assertTrue(ex.getSuggestion().contains("--backup-password"));
    }

    @Test
    void testEncryptionExceptionWrongPassword() {
        EncryptionException ex = new EncryptionException(true);
        assertEquals(4, ex.getExitCode());
        assertTrue(ex.wasPasswordProvided());
        assertTrue(ex.getMessage().contains("incorrect"));
    }

    @Test
    void testNoVoicemailsException() {
        NoVoicemailsException ex = new NoVoicemailsException();
        assertEquals(5, ex.getExitCode());
        assertTrue(ex.getMessage().contains("No voicemails found"));
    }

    @Test
    void testPermissionException() {
        Path path = Paths.get("/denied");
        PermissionException ex = new PermissionException(
            path,
            PermissionException.PermissionType.READ
        );
        assertEquals(7, ex.getExitCode());
        assertEquals(path, ex.getDeniedPath());
        assertEquals(PermissionException.PermissionType.READ, ex.getPermissionType());
    }

    @Test
    void testInsufficientStorageException() {
        Path path = Paths.get("/tmp");
        long required = 100 * 1024 * 1024; // 100 MB
        long available = 50 * 1024 * 1024;  // 50 MB

        InsufficientStorageException ex = new InsufficientStorageException(
            path, required, available
        );

        assertEquals(8, ex.getExitCode());
        assertEquals(path, ex.getLocation());
        assertEquals(required, ex.getRequiredBytes());
        assertEquals(available, ex.getAvailableBytes());
        assertTrue(ex.getMessage().contains("100"));
        assertTrue(ex.getMessage().contains("50"));
    }

    @Test
    void testConversionException() {
        Path path = Paths.get("/input.amr");
        ConversionException ex = new ConversionException(
            path,
            "Conversion failed",
            "Invalid data found"
        );

        assertEquals(1, ex.getExitCode());
        assertEquals(path, ex.getInputFile());
        assertTrue(ex.hasFfmpegError());
        assertTrue(ex.getSuggestion().contains("corrupted"));
    }
}
```

---

## Implementation Checklist

### Phase 1: Base Exception
- [ ] Implement `VoicemailConverterException`
- [ ] Write tests

### Phase 2: Dependency Errors
- [ ] Implement `DependencyException`
- [ ] Write tests
- [ ] Test suggestion formatting

### Phase 3: Backup Errors
- [ ] Implement `BackupException`
- [ ] Implement `EncryptionException`
- [ ] Implement `NoVoicemailsException`
- [ ] Write tests for all three

### Phase 4: Resource Errors
- [ ] Implement `PermissionException`
- [ ] Implement `InsufficientStorageException`
- [ ] Write tests
- [ ] Test byte formatting

### Phase 5: Conversion Errors
- [ ] Implement `ConversionException`
- [ ] Write tests
- [ ] Test FFmpeg error parsing

---

## Usage Examples

### In BackupDiscovery
```java
if (!Files.exists(backupDir)) {
    throw new BackupException(
        backupDir,
        "Backup directory does not exist: " + backupDir,
        "Create an iOS backup via iTunes/Finder first"
    );
}
```

### In FFmpegWrapper
```java
if (!isFFmpegAvailable()) {
    throw new DependencyException("ffmpeg", "FFmpeg not found in PATH");
}
```

### In VoicemailExtractor
```java
if (voicemails.isEmpty()) {
    throw new NoVoicemailsException();
}
```

### In FileOrganizer
```java
long required = calculateRequiredSpace(files);
long available = Files.getFileStore(outputDir).getUsableSpace();

if (available < required) {
    throw new InsufficientStorageException(outputDir, required, available);
}
```

---

## Common Pitfalls

1. **Don't swallow exceptions** - Always preserve the cause chain
2. **Use specific exceptions** - Don't use generic Exception
3. **Provide helpful suggestions** - Users should know how to fix the issue
4. **Test error messages** - Ensure they're user-friendly
5. **Include context** - Path, file name, error details

---

**End of Exception Module Implementation Guide**
