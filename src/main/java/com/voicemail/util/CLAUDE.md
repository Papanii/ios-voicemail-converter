# Util Module Implementation Guide

**Module:** Utilities
**Package:** `com.voicemail.util`
**Status:** Not Implemented
**Priority:** High (Foundation for other modules)

---

## Overview

The Util module provides common utility classes used throughout the application. These utilities handle cross-cutting concerns like logging, validation, temp file management, and filesystem operations.

## Module Purpose

Provide utilities for:
1. Temporary directory management
2. File system operations
3. Validation helpers
4. Platform detection
5. String formatting
6. Hashing (SHA-1 for backup files)

---

## Implementation Order

1. **PlatformUtil** - Platform detection (first)
2. **ValidationUtil** - Common validation logic
3. **TempDirectoryManager** - Temp file management
4. **FileSystemUtil** - File operations
5. **HashUtil** - SHA-1 hashing for backup files
6. **FormatUtil** - String formatting (bytes, durations, etc.)

---

## Class 1: PlatformUtil

**Location:** `src/main/java/com/voicemail/util/PlatformUtil.java`

### Implementation

```java
package com.voicemail.util;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Utility for platform detection and platform-specific paths
 */
public class PlatformUtil {

    public enum OperatingSystem {
        MAC_OS,
        WINDOWS,
        LINUX,
        UNKNOWN
    }

    private static OperatingSystem cachedOS;

    /**
     * Detect the current operating system
     */
    public static OperatingSystem getOperatingSystem() {
        if (cachedOS != null) {
            return cachedOS;
        }

        String os = System.getProperty("os.name").toLowerCase();

        if (os.contains("mac") || os.contains("darwin")) {
            cachedOS = OperatingSystem.MAC_OS;
        } else if (os.contains("win")) {
            cachedOS = OperatingSystem.WINDOWS;
        } else if (os.contains("nix") || os.contains("nux") || os.contains("aix")) {
            cachedOS = OperatingSystem.LINUX;
        } else {
            cachedOS = OperatingSystem.UNKNOWN;
        }

        return cachedOS;
    }

    /**
     * Get platform-specific default backup directory
     */
    public static Path getDefaultBackupDirectory() {
        String home = System.getProperty("user.home");

        switch (getOperatingSystem()) {
            case MAC_OS:
                return Paths.get(home, "Library/Application Support/MobileSync/Backup");

            case WINDOWS:
                String appData = System.getenv("APPDATA");
                if (appData == null) {
                    appData = home + "/AppData/Roaming";
                }
                return Paths.get(appData, "Apple Computer/MobileSync/Backup");

            case LINUX:
                return Paths.get(home, ".local/share/MobileSync/Backup");

            default:
                // Fallback
                return Paths.get(home, "MobileSync/Backup");
        }
    }

    /**
     * Get platform-specific line separator
     */
    public static String getLineSeparator() {
        return System.lineSeparator();
    }

    /**
     * Check if running on macOS
     */
    public static boolean isMacOS() {
        return getOperatingSystem() == OperatingSystem.MAC_OS;
    }

    /**
     * Check if running on Windows
     */
    public static boolean isWindows() {
        return getOperatingSystem() == OperatingSystem.WINDOWS;
    }

    /**
     * Check if running on Linux
     */
    public static boolean isLinux() {
        return getOperatingSystem() == OperatingSystem.LINUX;
    }

    /**
     * Get platform name for display
     */
    public static String getPlatformName() {
        return System.getProperty("os.name") + " " + System.getProperty("os.version");
    }
}
```

---

## Class 2: ValidationUtil

**Location:** `src/main/java/com/voicemail/util/ValidationUtil.java`

### Implementation

```java
package com.voicemail.util;

import java.util.regex.Pattern;

/**
 * Utility for common validation operations
 */
public class ValidationUtil {

    // UDID patterns
    private static final Pattern UDID_HEX_40 = Pattern.compile("^[0-9a-fA-F]{40}$");
    private static final Pattern UDID_UUID = Pattern.compile(
        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    );

    // Phone number pattern (E.164)
    private static final Pattern PHONE_E164 = Pattern.compile("^\\+[1-9]\\d{1,14}$");

    /**
     * Validate UDID format
     */
    public static boolean isValidUdid(String udid) {
        if (udid == null || udid.isEmpty()) {
            return false;
        }
        return UDID_HEX_40.matcher(udid).matches() || UDID_UUID.matcher(udid).matches();
    }

    /**
     * Validate phone number format (basic validation)
     */
    public static boolean isValidPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            return false;
        }
        // Basic check - contains only digits, +, -, (, ), spaces
        return phoneNumber.matches("[\\d\\s+\\-()]+");
    }

    /**
     * Validate E.164 phone number format
     */
    public static boolean isValidE164PhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            return false;
        }
        return PHONE_E164.matcher(phoneNumber).matches();
    }

    /**
     * Sanitize filename - remove invalid characters
     */
    public static String sanitizeFilename(String filename) {
        if (filename == null) {
            return "";
        }

        // Replace invalid characters with underscore
        // Invalid: \ / : * ? " < > |
        String sanitized = filename.replaceAll("[\\\\/:*?\"<>|]", "_");

        // Replace multiple underscores with single
        sanitized = sanitized.replaceAll("_+", "_");

        // Trim leading/trailing underscores
        sanitized = sanitized.replaceAll("^_+|_+$", "");

        // Limit length (255 is common filesystem limit)
        if (sanitized.length() > 255) {
            sanitized = sanitized.substring(0, 255);
        }

        return sanitized;
    }

    /**
     * Normalize phone number for filename use
     */
    public static String normalizePhoneForFilename(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isEmpty() || "Unknown".equalsIgnoreCase(phoneNumber)) {
            return "Unknown";
        }

        // Remove all non-digit characters except leading +
        String normalized = phoneNumber.replaceAll("[^0-9+]", "");

        // Ensure + is only at start
        if (normalized.contains("+")) {
            normalized = "+" + normalized.replaceAll("\\+", "");
        }

        // Limit length for filenames
        if (normalized.length() > 20) {
            normalized = normalized.substring(0, 20);
        }

        return normalized.isEmpty() ? "Unknown" : normalized;
    }

    /**
     * Format phone number for display (E.164 -> formatted)
     */
    public static String formatPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            return "Unknown";
        }

        // Basic US format: +1-234-567-8900
        if (phoneNumber.startsWith("+1") && phoneNumber.length() == 12) {
            return phoneNumber.substring(0, 2) + "-" +
                   phoneNumber.substring(2, 5) + "-" +
                   phoneNumber.substring(5, 8) + "-" +
                   phoneNumber.substring(8);
        }

        return phoneNumber;
    }

    /**
     * Check if string is null or empty
     */
    public static boolean isNullOrEmpty(String str) {
        return str == null || str.isEmpty();
    }

    /**
     * Check if string is null, empty, or whitespace
     */
    public static boolean isNullOrBlank(String str) {
        return str == null || str.trim().isEmpty();
    }
}
```

---

## Class 3: TempDirectoryManager

**Location:** `src/main/java/com/voicemail/util/TempDirectoryManager.java`

### Implementation

```java
package com.voicemail.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Manages temporary directory for voicemail processing
 */
public class TempDirectoryManager {
    private static final Logger log = LoggerFactory.getLogger(TempDirectoryManager.class);
    private static final String PREFIX = "voicemail-converter-";
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private Path tempDirectory;

    /**
     * Create and return temp directory
     */
    public Path createTempDirectory() throws IOException {
        if (tempDirectory != null && Files.exists(tempDirectory)) {
            log.debug("Temp directory already exists: {}", tempDirectory);
            return tempDirectory;
        }

        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        String dirName = PREFIX + timestamp;

        tempDirectory = Files.createTempDirectory(dirName);
        log.info("Created temp directory: {}", tempDirectory);

        // Add shutdown hook to clean up
        addShutdownHook();

        return tempDirectory;
    }

    /**
     * Get the current temp directory
     */
    public Path getTempDirectory() {
        return tempDirectory;
    }

    /**
     * Create subdirectory in temp directory
     */
    public Path createSubdirectory(String name) throws IOException {
        if (tempDirectory == null) {
            throw new IllegalStateException("Temp directory not created yet");
        }

        Path subdir = tempDirectory.resolve(name);
        if (!Files.exists(subdir)) {
            Files.createDirectories(subdir);
            log.debug("Created subdirectory: {}", subdir);
        }

        return subdir;
    }

    /**
     * Clean up temp directory
     */
    public void cleanup() {
        if (tempDirectory == null || !Files.exists(tempDirectory)) {
            return;
        }

        try {
            log.info("Cleaning up temp directory: {}", tempDirectory);
            deleteDirectoryRecursively(tempDirectory);
            tempDirectory = null;
        } catch (IOException e) {
            log.warn("Failed to delete temp directory: {}", tempDirectory, e);
        }
    }

    /**
     * Delete directory recursively
     */
    private void deleteDirectoryRecursively(Path directory) throws IOException {
        Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Add shutdown hook for cleanup
     */
    private void addShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.debug("Shutdown hook triggered, cleaning up temp directory");
            cleanup();
        }));
    }

    /**
     * Clean up old temp directories (older than 1 day)
     */
    public static void cleanupOldTempDirectories() {
        try {
            Path tempDir = Paths.get(System.getProperty("java.io.tmpdir"));

            Files.list(tempDir)
                .filter(p -> p.getFileName().toString().startsWith(PREFIX))
                .filter(p -> {
                    try {
                        long modified = Files.getLastModifiedTime(p).toMillis();
                        long age = System.currentTimeMillis() - modified;
                        return age > 24 * 60 * 60 * 1000; // > 1 day
                    } catch (IOException e) {
                        return false;
                    }
                })
                .forEach(p -> {
                    try {
                        TempDirectoryManager temp = new TempDirectoryManager();
                        temp.tempDirectory = p;
                        temp.cleanup();
                        log.debug("Cleaned up old temp directory: {}", p);
                    } catch (Exception e) {
                        log.warn("Failed to clean up old temp directory: {}", p, e);
                    }
                });
        } catch (IOException e) {
            log.warn("Failed to list temp directories", e);
        }
    }
}
```

---

## Class 4: FileSystemUtil

**Location:** `src/main/java/com/voicemail/util/FileSystemUtil.java`

### Implementation

```java
package com.voicemail.util;

import com.voicemail.exception.InsufficientStorageException;
import com.voicemail.exception.PermissionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Utility for file system operations
 */
public class FileSystemUtil {
    private static final Logger log = LoggerFactory.getLogger(FileSystemUtil.class);

    /**
     * Ensure directory exists, create if necessary
     */
    public static void ensureDirectoryExists(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            log.debug("Creating directory: {}", directory);
            Files.createDirectories(directory);
        } else if (!Files.isDirectory(directory)) {
            throw new IOException("Path exists but is not a directory: " + directory);
        }
    }

    /**
     * Check if path is readable
     */
    public static void ensureReadable(Path path) throws PermissionException {
        if (!Files.isReadable(path)) {
            throw new PermissionException(path, PermissionException.PermissionType.READ);
        }
    }

    /**
     * Check if path is writable
     */
    public static void ensureWritable(Path path) throws PermissionException {
        if (!Files.isWritable(path)) {
            throw new PermissionException(path, PermissionException.PermissionType.WRITE);
        }
    }

    /**
     * Check if sufficient disk space is available
     */
    public static void checkDiskSpace(Path location, long requiredBytes)
            throws IOException, InsufficientStorageException {

        long available = Files.getFileStore(location).getUsableSpace();

        if (available < requiredBytes) {
            throw new InsufficientStorageException(location, requiredBytes, available);
        }
    }

    /**
     * Get available disk space
     */
    public static long getAvailableSpace(Path location) throws IOException {
        return Files.getFileStore(location).getUsableSpace();
    }

    /**
     * Copy file with progress logging
     */
    public static void copyFile(Path source, Path destination) throws IOException {
        log.debug("Copying: {} -> {}", source, destination);

        // Ensure parent directory exists
        Path parent = destination.getParent();
        if (parent != null) {
            ensureDirectoryExists(parent);
        }

        Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Move file
     */
    public static void moveFile(Path source, Path destination) throws IOException {
        log.debug("Moving: {} -> {}", source, destination);

        Path parent = destination.getParent();
        if (parent != null) {
            ensureDirectoryExists(parent);
        }

        Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Calculate directory size recursively
     */
    public static long calculateDirectorySize(Path directory) throws IOException {
        final long[] size = {0};

        Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                size[0] += attrs.size();
                return FileVisitResult.CONTINUE;
            }
        });

        return size[0];
    }

    /**
     * Count files in directory (non-recursive)
     */
    public static long countFiles(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return 0;
        }

        try (var stream = Files.list(directory)) {
            return stream.filter(Files::isRegularFile).count();
        }
    }

    /**
     * Generate unique filename if file exists
     */
    public static Path generateUniqueFilename(Path path) {
        if (!Files.exists(path)) {
            return path;
        }

        String fileName = path.getFileName().toString();
        Path parent = path.getParent();

        // Split name and extension
        int dotIndex = fileName.lastIndexOf('.');
        String name = dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
        String ext = dotIndex > 0 ? fileName.substring(dotIndex) : "";

        // Try appending _001, _002, etc.
        int counter = 1;
        Path newPath;
        do {
            String newFileName = String.format("%s_%03d%s", name, counter, ext);
            newPath = parent != null ? parent.resolve(newFileName) : Paths.get(newFileName);
            counter++;
        } while (Files.exists(newPath) && counter < 1000);

        if (Files.exists(newPath)) {
            throw new IllegalStateException("Could not generate unique filename after 1000 attempts");
        }

        return newPath;
    }

    /**
     * Check if file is empty
     */
    public static boolean isEmpty(Path file) throws IOException {
        if (!Files.exists(file)) {
            return true;
        }
        return Files.size(file) == 0;
    }
}
```

---

## Class 5: HashUtil

**Location:** `src/main/java/com/voicemail/util/HashUtil.java`

### Implementation

```java
package com.voicemail.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Utility for hashing operations (SHA-1 for iOS backup files)
 */
public class HashUtil {

    /**
     * Calculate SHA-1 hash of string
     */
    public static String sha1(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hashBytes = digest.digest(input.getBytes());
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-1 algorithm not available", e);
        }
    }

    /**
     * Calculate iOS backup file hash (domain + "-" + relativePath)
     */
    public static String calculateBackupFileHash(String domain, String relativePath) {
        String combined = domain + "-" + relativePath;
        return sha1(combined);
    }

    /**
     * Get backup file path from hash (first 2 chars as directory)
     */
    public static String getBackupFilePath(String hash) {
        if (hash.length() < 2) {
            throw new IllegalArgumentException("Hash too short: " + hash);
        }
        return hash.substring(0, 2) + "/" + hash;
    }

    /**
     * Convert byte array to hex string
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
```

---

## Class 6: FormatUtil

**Location:** `src/main/java/com/voicemail/util/FormatUtil.java`

### Implementation

```java
package com.voicemail.util;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Utility for formatting strings (bytes, durations, timestamps)
 */
public class FormatUtil {

    private static final DateTimeFormatter FILENAME_TIMESTAMP =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss");

    private static final DateTimeFormatter DISPLAY_TIMESTAMP =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Format bytes in human-readable format
     */
    public static String formatBytes(long bytes) {
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

    /**
     * Format duration in human-readable format
     */
    public static String formatDuration(Duration duration) {
        long seconds = duration.getSeconds();

        if (seconds < 60) {
            return seconds + "s";
        } else if (seconds < 3600) {
            long minutes = seconds / 60;
            long secs = seconds % 60;
            return String.format("%dm %ds", minutes, secs);
        } else {
            long hours = seconds / 3600;
            long minutes = (seconds % 3600) / 60;
            return String.format("%dh %dm", hours, minutes);
        }
    }

    /**
     * Format timestamp for filename (YYYY-MM-DDTHH-mm-ss)
     */
    public static String formatTimestampForFilename(Instant instant) {
        LocalDateTime ldt = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
        return ldt.format(FILENAME_TIMESTAMP);
    }

    /**
     * Format timestamp for display (YYYY-MM-DD HH:mm:ss)
     */
    public static String formatTimestampForDisplay(Instant instant) {
        LocalDateTime ldt = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
        return ldt.format(DISPLAY_TIMESTAMP);
    }

    /**
     * Format Unix timestamp for filename
     */
    public static String formatUnixTimestampForFilename(long unixTimestamp) {
        Instant instant = Instant.ofEpochSecond(unixTimestamp);
        return formatTimestampForFilename(instant);
    }

    /**
     * Format progress percentage
     */
    public static String formatProgress(int current, int total) {
        if (total == 0) {
            return "0%";
        }
        int percent = (int) ((current * 100.0) / total);
        return String.format("%d/%d (%d%%)", current, total, percent);
    }

    /**
     * Format progress bar
     */
    public static String formatProgressBar(int current, int total, int width) {
        if (total == 0) {
            return "[" + " ".repeat(width) + "]";
        }

        int filled = (int) ((current * width) / (double) total);
        int empty = width - filled;

        return "[" + "=".repeat(filled) + " ".repeat(empty) + "]";
    }
}
```

---

## Testing Utilities

**Test file:** `src/test/java/com/voicemail/util/UtilTest.java`

```java
package com.voicemail.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class UtilTest {

    @Test
    void testPlatformDetection() {
        PlatformUtil.OperatingSystem os = PlatformUtil.getOperatingSystem();
        assertNotNull(os);
        assertNotEquals(PlatformUtil.OperatingSystem.UNKNOWN, os);
    }

    @Test
    void testDefaultBackupDirectory() {
        Path backupDir = PlatformUtil.getDefaultBackupDirectory();
        assertNotNull(backupDir);
        assertTrue(backupDir.toString().contains("MobileSync"));
    }

    @Test
    void testValidUdid() {
        assertTrue(ValidationUtil.isValidUdid("1234567890abcdef1234567890abcdef12345678"));
        assertTrue(ValidationUtil.isValidUdid("12345678-1234-1234-1234-123456789012"));
        assertFalse(ValidationUtil.isValidUdid("invalid"));
        assertFalse(ValidationUtil.isValidUdid(null));
    }

    @Test
    void testSanitizeFilename() {
        assertEquals("test", ValidationUtil.sanitizeFilename("test"));
        assertEquals("test_file", ValidationUtil.sanitizeFilename("test:file"));
        assertEquals("test_file", ValidationUtil.sanitizeFilename("test/file"));
        assertEquals("test_file", ValidationUtil.sanitizeFilename("test*file"));
    }

    @Test
    void testNormalizePhoneForFilename() {
        assertEquals("+1234567890", ValidationUtil.normalizePhoneForFilename("+1-234-567-8900"));
        assertEquals("+1234567890", ValidationUtil.normalizePhoneForFilename("(234) 567-8900"));
        assertEquals("Unknown", ValidationUtil.normalizePhoneForFilename("Unknown"));
        assertEquals("Unknown", ValidationUtil.normalizePhoneForFilename(""));
    }

    @Test
    void testTempDirectoryManager(@TempDir Path tempDir) throws IOException {
        TempDirectoryManager manager = new TempDirectoryManager();
        Path temp = manager.createTempDirectory();

        assertNotNull(temp);
        assertTrue(Files.exists(temp));

        Path subdir = manager.createSubdirectory("test");
        assertTrue(Files.exists(subdir));

        manager.cleanup();
        assertFalse(Files.exists(temp));
    }

    @Test
    void testFileSystemUtilEnsureDirectory(@TempDir Path tempDir) throws IOException {
        Path newDir = tempDir.resolve("newdir");
        assertFalse(Files.exists(newDir));

        FileSystemUtil.ensureDirectoryExists(newDir);
        assertTrue(Files.exists(newDir));
        assertTrue(Files.isDirectory(newDir));
    }

    @Test
    void testGenerateUniqueFilename(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("test.txt");
        Files.createFile(file);

        Path unique = FileSystemUtil.generateUniqueFilename(file);
        assertNotEquals(file, unique);
        assertTrue(unique.toString().contains("_001"));
    }

    @Test
    void testHashUtil() {
        String hash = HashUtil.sha1("test");
        assertNotNull(hash);
        assertEquals(40, hash.length());

        String backupHash = HashUtil.calculateBackupFileHash("Library-Voicemail", "voicemail.db");
        assertEquals(40, backupHash.length());

        String path = HashUtil.getBackupFilePath(backupHash);
        assertTrue(path.contains("/"));
        assertEquals(backupHash.substring(0, 2), path.substring(0, 2));
    }

    @Test
    void testFormatBytes() {
        assertEquals("512 B", FormatUtil.formatBytes(512));
        assertEquals("1.0 KB", FormatUtil.formatBytes(1024));
        assertEquals("1.0 MB", FormatUtil.formatBytes(1024 * 1024));
        assertEquals("1.0 GB", FormatUtil.formatBytes(1024L * 1024 * 1024));
    }

    @Test
    void testFormatDuration() {
        assertEquals("30s", FormatUtil.formatDuration(Duration.ofSeconds(30)));
        assertEquals("2m 30s", FormatUtil.formatDuration(Duration.ofSeconds(150)));
        assertEquals("1h 30m", FormatUtil.formatDuration(Duration.ofSeconds(5400)));
    }

    @Test
    void testFormatTimestamp() {
        Instant now = Instant.now();
        String filename = FormatUtil.formatTimestampForFilename(now);
        assertNotNull(filename);
        assertTrue(filename.contains("T"));
        assertTrue(filename.contains("-"));

        String display = FormatUtil.formatTimestampForDisplay(now);
        assertNotNull(display);
        assertTrue(display.contains(" "));
    }

    @Test
    void testFormatProgress() {
        assertEquals("5/10 (50%)", FormatUtil.formatProgress(5, 10));
        assertEquals("0/10 (0%)", FormatUtil.formatProgress(0, 10));
        assertEquals("10/10 (100%)", FormatUtil.formatProgress(10, 10));
    }

    @Test
    void testFormatProgressBar() {
        String bar = FormatUtil.formatProgressBar(5, 10, 20);
        assertEquals(22, bar.length()); // [ + 20 chars + ]
        assertTrue(bar.contains("="));
        assertTrue(bar.contains(" "));
    }
}
```

---

## Implementation Checklist

- [ ] Implement `PlatformUtil`
- [ ] Implement `ValidationUtil`
- [ ] Implement `TempDirectoryManager`
- [ ] Implement `FileSystemUtil`
- [ ] Implement `HashUtil`
- [ ] Implement `FormatUtil`
- [ ] Write comprehensive tests for all classes
- [ ] Test on different platforms

---

## Usage in Other Modules

### In CLI Parser
```java
Path defaultBackup = PlatformUtil.getDefaultBackupDirectory();
String sanitized = ValidationUtil.sanitizeFilename(userInput);
```

### In Extractor
```java
String hash = HashUtil.calculateBackupFileHash("Library-Voicemail", "voicemail.db");
String filePath = HashUtil.getBackupFilePath(hash);
```

### In Converter
```java
TempDirectoryManager tempMgr = new TempDirectoryManager();
Path temp = tempMgr.createTempDirectory();
// ... do work
tempMgr.cleanup();
```

### In Output
```java
String filename = FormatUtil.formatTimestampForFilename(instant);
FileSystemUtil.copyFile(source, destination);
```

---

**End of Util Module Implementation Guide**
