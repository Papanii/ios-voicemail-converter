# Backup Module Implementation Guide

**Module:** Backup Discovery and Validation
**Package:** `com.voicemail.backup`
**Status:** Not Implemented
**Priority:** High (Required before extraction)

---

## Overview

The Backup module is responsible for discovering iOS backups, parsing their metadata, validating integrity, and selecting the appropriate backup to process.

## Module Purpose

Handle:
1. Backup discovery (enumerate backups in directory)
2. Plist parsing (Info.plist, Manifest.plist)
3. Backup validation (integrity, completeness)
4. Backup selection (single vs multiple backups)
5. Encryption detection and handling

---

## Dependencies

### External Libraries
- **dd-plist 1.28** - Parse Apple plist files (already in pom.xml)

### Internal Dependencies
- `com.voicemail.exception.*` - Exception classes
- `com.voicemail.util.*` - Utility classes

---

## Implementation Order

1. **BackupInfo** - Data class for backup metadata (first)
2. **PlistParser** - Parse plist files
3. **BackupValidator** - Validate backup integrity
4. **BackupDiscovery** - Main orchestrator (last)

---

## Class 1: BackupInfo

**Location:** `src/main/java/com/voicemail/backup/BackupInfo.java`

### Implementation

```java
package com.voicemail.backup;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Immutable data class containing iOS backup metadata
 */
public class BackupInfo {
    private final String udid;
    private final String deviceName;
    private final String displayName;
    private final String productType;
    private final String productVersion;  // iOS version
    private final String serialNumber;
    private final String phoneNumber;
    private final LocalDateTime lastBackupDate;
    private final boolean isEncrypted;
    private final Path backupPath;

    private BackupInfo(Builder builder) {
        this.udid = Objects.requireNonNull(builder.udid, "udid is required");
        this.deviceName = builder.deviceName;
        this.displayName = builder.displayName;
        this.productType = builder.productType;
        this.productVersion = builder.productVersion;
        this.serialNumber = builder.serialNumber;
        this.phoneNumber = builder.phoneNumber;
        this.lastBackupDate = builder.lastBackupDate;
        this.isEncrypted = builder.isEncrypted;
        this.backupPath = Objects.requireNonNull(builder.backupPath, "backupPath is required");
    }

    // Getters
    public String getUdid() { return udid; }
    public String getDeviceName() { return deviceName; }
    public String getDisplayName() { return displayName; }
    public String getProductType() { return productType; }
    public String getProductVersion() { return productVersion; }
    public String getSerialNumber() { return serialNumber; }
    public String getPhoneNumber() { return phoneNumber; }
    public LocalDateTime getLastBackupDate() { return lastBackupDate; }
    public boolean isEncrypted() { return isEncrypted; }
    public Path getBackupPath() { return backupPath; }

    /**
     * Get friendly device description (e.g., "iPhone 13 Pro")
     */
    public String getDeviceDescription() {
        if (productType != null) {
            return mapProductTypeToName(productType);
        }
        return deviceName != null ? deviceName : "Unknown Device";
    }

    /**
     * Map product type to friendly name
     */
    private String mapProductTypeToName(String productType) {
        // Common mappings
        switch (productType) {
            case "iPhone14,2": return "iPhone 13 Pro";
            case "iPhone14,3": return "iPhone 13 Pro Max";
            case "iPhone14,4": return "iPhone 13 mini";
            case "iPhone14,5": return "iPhone 13";
            case "iPhone15,2": return "iPhone 14 Pro";
            case "iPhone15,3": return "iPhone 14 Pro Max";
            case "iPhone15,4": return "iPhone 14";
            case "iPhone15,5": return "iPhone 14 Plus";
            case "iPhone16,1": return "iPhone 15 Pro";
            case "iPhone16,2": return "iPhone 15 Pro Max";
            // Add more mappings as needed
            default: return productType;
        }
    }

    @Override
    public String toString() {
        return String.format("%s (iOS %s) - Last backup: %s [%s]",
            deviceName != null ? deviceName : "Unknown",
            productVersion != null ? productVersion : "Unknown",
            lastBackupDate != null ? lastBackupDate : "Unknown",
            udid
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BackupInfo)) return false;
        BackupInfo that = (BackupInfo) o;
        return Objects.equals(udid, that.udid) &&
               Objects.equals(backupPath, that.backupPath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(udid, backupPath);
    }

    /**
     * Builder for BackupInfo
     */
    public static class Builder {
        private String udid;
        private String deviceName;
        private String displayName;
        private String productType;
        private String productVersion;
        private String serialNumber;
        private String phoneNumber;
        private LocalDateTime lastBackupDate;
        private boolean isEncrypted;
        private Path backupPath;

        public Builder udid(String udid) {
            this.udid = udid;
            return this;
        }

        public Builder deviceName(String deviceName) {
            this.deviceName = deviceName;
            return this;
        }

        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder productType(String productType) {
            this.productType = productType;
            return this;
        }

        public Builder productVersion(String productVersion) {
            this.productVersion = productVersion;
            return this;
        }

        public Builder serialNumber(String serialNumber) {
            this.serialNumber = serialNumber;
            return this;
        }

        public Builder phoneNumber(String phoneNumber) {
            this.phoneNumber = phoneNumber;
            return this;
        }

        public Builder lastBackupDate(LocalDateTime lastBackupDate) {
            this.lastBackupDate = lastBackupDate;
            return this;
        }

        public Builder isEncrypted(boolean isEncrypted) {
            this.isEncrypted = isEncrypted;
            return this;
        }

        public Builder backupPath(Path backupPath) {
            this.backupPath = backupPath;
            return this;
        }

        public BackupInfo build() {
            return new BackupInfo(this);
        }
    }
}
```

---

## Class 2: PlistParser

**Location:** `src/main/java/com/voicemail/backup/PlistParser.java`

### Implementation

```java
package com.voicemail.backup;

import com.dd.plist.NSDictionary;
import com.dd.plist.NSDate;
import com.dd.plist.PropertyListParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

/**
 * Parser for Apple plist files (Info.plist, Manifest.plist)
 */
public class PlistParser {
    private static final Logger log = LoggerFactory.getLogger(PlistParser.class);

    /**
     * Parse Info.plist and extract device information
     */
    public static BackupInfo.Builder parseInfoPlist(Path infoPlist, Path backupPath) throws Exception {
        log.debug("Parsing Info.plist: {}", infoPlist);

        NSDictionary rootDict = (NSDictionary) PropertyListParser.parse(infoPlist.toFile());
        BackupInfo.Builder builder = new BackupInfo.Builder();

        builder.backupPath(backupPath);

        // Extract fields
        if (rootDict.containsKey("Device Name")) {
            builder.deviceName(rootDict.get("Device Name").toString());
        }

        if (rootDict.containsKey("Display Name")) {
            builder.displayName(rootDict.get("Display Name").toString());
        }

        if (rootDict.containsKey("Product Type")) {
            builder.productType(rootDict.get("Product Type").toString());
        }

        if (rootDict.containsKey("Product Version")) {
            builder.productVersion(rootDict.get("Product Version").toString());
        }

        if (rootDict.containsKey("Serial Number")) {
            builder.serialNumber(rootDict.get("Serial Number").toString());
        }

        if (rootDict.containsKey("Phone Number")) {
            builder.phoneNumber(rootDict.get("Phone Number").toString());
        }

        if (rootDict.containsKey("Unique Identifier")) {
            builder.udid(rootDict.get("Unique Identifier").toString());
        } else if (rootDict.containsKey("UDID")) {
            builder.udid(rootDict.get("UDID").toString());
        } else {
            // Use directory name as fallback
            builder.udid(backupPath.getFileName().toString());
        }

        if (rootDict.containsKey("Last Backup Date")) {
            NSDate lastBackup = (NSDate) rootDict.get("Last Backup Date");
            builder.lastBackupDate(convertNSDateToLocalDateTime(lastBackup));
        }

        log.debug("Parsed device: {}", builder.build().getDeviceName());
        return builder;
    }

    /**
     * Parse Manifest.plist and extract backup metadata
     */
    public static void parseManifestPlist(Path manifestPlist, BackupInfo.Builder builder) throws Exception {
        log.debug("Parsing Manifest.plist: {}", manifestPlist);

        NSDictionary rootDict = (NSDictionary) PropertyListParser.parse(manifestPlist.toFile());

        // Check encryption status
        if (rootDict.containsKey("IsEncrypted")) {
            boolean encrypted = Boolean.parseBoolean(rootDict.get("IsEncrypted").toString());
            builder.isEncrypted(encrypted);
            log.debug("Backup encrypted: {}", encrypted);
        }

        // Extract backup date from Manifest if not in Info
        if (rootDict.containsKey("Date")) {
            NSDate backupDate = (NSDate) rootDict.get("Date");
            builder.lastBackupDate(convertNSDateToLocalDateTime(backupDate));
        }
    }

    /**
     * Convert NSDate to LocalDateTime
     */
    private static LocalDateTime convertNSDateToLocalDateTime(NSDate nsDate) {
        Date javaDate = nsDate.getDate();
        return LocalDateTime.ofInstant(javaDate.toInstant(), ZoneId.systemDefault());
    }

    /**
     * Check if plist file is valid
     */
    public static boolean isValidPlist(Path plistFile) {
        try {
            PropertyListParser.parse(plistFile.toFile());
            return true;
        } catch (Exception e) {
            log.warn("Invalid plist file: {}", plistFile, e);
            return false;
        }
    }
}
```

---

## Class 3: BackupValidator

**Location:** `src/main/java/com/voicemail/backup/BackupValidator.java`

### Implementation

```java
package com.voicemail.backup;

import com.voicemail.exception.BackupException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Validates iOS backup integrity and completeness
 */
public class BackupValidator {
    private static final Logger log = LoggerFactory.getLogger(BackupValidator.class);

    /**
     * Validate backup directory structure and files
     */
    public static void validateBackup(BackupInfo backup) throws BackupException {
        log.debug("Validating backup: {}", backup.getBackupPath());

        validateRequiredFiles(backup);
        validateManifestDatabase(backup);
        validateIOSVersion(backup);
        checkBackupComplete(backup);

        log.info("Backup validation successful: {}", backup.getDeviceName());
    }

    /**
     * Check required files exist
     */
    private static void validateRequiredFiles(BackupInfo backup) throws BackupException {
        Path backupPath = backup.getBackupPath();

        // Required files
        String[] requiredFiles = {
            "Info.plist",
            "Manifest.plist",
            "Manifest.db"
        };

        for (String filename : requiredFiles) {
            Path file = backupPath.resolve(filename);
            if (!Files.exists(file)) {
                throw new BackupException(
                    "Required file missing: " + filename,
                    "Backup may be corrupted. Create a new backup."
                );
            }
        }
    }

    /**
     * Validate Manifest.db is a valid SQLite database
     */
    private static void validateManifestDatabase(BackupInfo backup) throws BackupException {
        Path manifestDb = backup.getBackupPath().resolve("Manifest.db");

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + manifestDb)) {
            // Try to query Files table
            var stmt = conn.createStatement();
            var rs = stmt.executeQuery("SELECT COUNT(*) FROM Files");

            if (rs.next()) {
                int fileCount = rs.getInt(1);
                log.debug("Manifest.db contains {} files", fileCount);

                if (fileCount == 0) {
                    throw new BackupException(
                        "Manifest database is empty",
                        "Backup may be incomplete. Create a new backup."
                    );
                }
            }
        } catch (SQLException e) {
            throw new BackupException(
                "Manifest database is corrupted or invalid",
                "Create a new backup.",
                e
            );
        }
    }

    /**
     * Validate iOS version is compatible (>= 7.0)
     */
    private static void validateIOSVersion(BackupInfo backup) throws BackupException {
        String version = backup.getProductVersion();

        if (version == null || version.isEmpty()) {
            log.warn("iOS version unknown, skipping version check");
            return;
        }

        try {
            // Extract major version (e.g., "17.5.1" -> 17)
            String[] parts = version.split("\\.");
            int majorVersion = Integer.parseInt(parts[0]);

            if (majorVersion < 7) {
                throw new BackupException(
                    "iOS version " + version + " is too old",
                    "Voicemail backup is only supported on iOS 7.0 and later"
                );
            }

            log.debug("iOS version {} is compatible", version);

        } catch (NumberFormatException e) {
            log.warn("Could not parse iOS version: {}", version);
        }
    }

    /**
     * Check if backup is complete (not in progress)
     */
    private static void checkBackupComplete(BackupInfo backup) throws BackupException {
        Path statusPlist = backup.getBackupPath().resolve("Status.plist");

        if (!Files.exists(statusPlist)) {
            // Status.plist may not exist in older backups, that's okay
            log.debug("Status.plist not found, assuming backup is complete");
            return;
        }

        try {
            // Parse Status.plist to check completion status
            // For now, we'll just check it exists and is readable
            if (!PlistParser.isValidPlist(statusPlist)) {
                throw new BackupException(
                    "Status.plist is corrupted",
                    "Backup may be incomplete. Create a new backup."
                );
            }

            log.debug("Backup appears complete");

        } catch (Exception e) {
            log.warn("Could not validate Status.plist", e);
        }
    }

    /**
     * Estimate backup age and warn if old
     */
    public static void checkBackupAge(BackupInfo backup) {
        if (backup.getLastBackupDate() == null) {
            log.warn("Backup date unknown");
            return;
        }

        var backupDate = backup.getLastBackupDate();
        var now = java.time.LocalDateTime.now();
        var daysSinceBackup = java.time.Duration.between(backupDate, now).toDays();

        if (daysSinceBackup > 30) {
            log.warn("Backup is {} days old. Voicemails after {} won't be included.",
                daysSinceBackup, backupDate.toLocalDate());
        } else if (daysSinceBackup > 7) {
            log.info("Backup is {} days old ({})", daysSinceBackup, backupDate.toLocalDate());
        }
    }
}
```

---

## Class 4: BackupDiscovery

**Location:** `src/main/java/com/voicemail/backup/BackupDiscovery.java`

### Implementation

```java
package com.voicemail.backup;

import com.voicemail.cli.Arguments;
import com.voicemail.exception.BackupException;
import com.voicemail.util.ValidationUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Discovers and selects iOS backups
 */
public class BackupDiscovery {
    private static final Logger log = LoggerFactory.getLogger(BackupDiscovery.class);

    /**
     * Discover and select backup based on arguments
     */
    public BackupInfo discoverBackup(Arguments args) throws BackupException {
        log.info("Discovering iOS backups in: {}", args.getBackupDir());

        // Enumerate all backups
        List<BackupInfo> backups = enumerateBackups(args.getBackupDir());

        if (backups.isEmpty()) {
            throw new BackupException(
                "No iOS backups found in " + args.getBackupDir(),
                "Create an iOS backup via iTunes/Finder first:\n" +
                "  1. Connect iPhone to computer\n" +
                "  2. Open Finder (macOS) or iTunes (Windows)\n" +
                "  3. Select device and click 'Back Up Now'\n" +
                "  4. Wait for backup to complete\n" +
                "  5. Run this tool again"
            );
        }

        log.info("Found {} backup(s)", backups.size());

        // Select backup
        BackupInfo selected;
        if (args.getDeviceId().isPresent()) {
            selected = selectBackupByDeviceId(backups, args.getDeviceId().get());
        } else {
            selected = selectBackupAutomatically(backups);
        }

        // Validate selected backup
        BackupValidator.validateBackup(selected);
        BackupValidator.checkBackupAge(selected);

        log.info("Selected backup: {}", selected);
        return selected;
    }

    /**
     * Enumerate all backups in directory
     */
    private List<BackupInfo> enumerateBackups(Path backupDir) throws BackupException {
        List<BackupInfo> backups = new ArrayList<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(backupDir)) {
            for (Path entry : stream) {
                if (!Files.isDirectory(entry)) {
                    continue;
                }

                // Check if looks like a UDID
                String dirName = entry.getFileName().toString();
                if (!isValidBackupDirectoryName(dirName)) {
                    log.debug("Skipping non-backup directory: {}", dirName);
                    continue;
                }

                try {
                    BackupInfo backup = parseBackup(entry);
                    backups.add(backup);
                    log.debug("Found backup: {}", backup);
                } catch (Exception e) {
                    log.warn("Failed to parse backup at {}: {}", entry, e.getMessage());
                }
            }
        } catch (IOException e) {
            throw new BackupException(
                "Failed to list backups in " + backupDir,
                "Check directory permissions",
                e
            );
        }

        return backups;
    }

    /**
     * Parse backup directory
     */
    private BackupInfo parseBackup(Path backupPath) throws Exception {
        Path infoPlist = backupPath.resolve("Info.plist");
        Path manifestPlist = backupPath.resolve("Manifest.plist");

        if (!Files.exists(infoPlist)) {
            throw new IOException("Info.plist not found");
        }

        // Parse Info.plist
        BackupInfo.Builder builder = PlistParser.parseInfoPlist(infoPlist, backupPath);

        // Parse Manifest.plist if exists
        if (Files.exists(manifestPlist)) {
            PlistParser.parseManifestPlist(manifestPlist, builder);
        }

        return builder.build();
    }

    /**
     * Check if directory name looks like a backup UDID
     */
    private boolean isValidBackupDirectoryName(String dirName) {
        // Check for UDID format (40 hex chars or UUID)
        return ValidationUtil.isValidUdid(dirName);
    }

    /**
     * Select backup by device ID
     */
    private BackupInfo selectBackupByDeviceId(List<BackupInfo> backups, String deviceId)
            throws BackupException {

        for (BackupInfo backup : backups) {
            if (backup.getUdid().equals(deviceId)) {
                log.info("Found backup for device ID: {}", deviceId);
                return backup;
            }
        }

        // Build error message with available backups
        StringBuilder msg = new StringBuilder();
        msg.append("No backup found for device ID: ").append(deviceId).append("\n\n");
        msg.append("Available backups:\n");
        for (BackupInfo backup : backups) {
            msg.append("  - ").append(backup.toString()).append("\n");
        }

        throw new BackupException(
            msg.toString(),
            "Use one of the UDIDs shown above with --device-id"
        );
    }

    /**
     * Select backup automatically
     */
    private BackupInfo selectBackupAutomatically(List<BackupInfo> backups) throws BackupException {
        if (backups.size() == 1) {
            log.info("Single backup found, using it");
            return backups.get(0);
        }

        // Multiple backups - need user to specify
        StringBuilder msg = new StringBuilder();
        msg.append("Multiple backups found. Please specify device with --device-id:\n\n");

        // Sort by date (newest first)
        backups.sort(Comparator.comparing(
            BackupInfo::getLastBackupDate,
            Comparator.nullsLast(Comparator.reverseOrder())
        ));

        int index = 1;
        for (BackupInfo backup : backups) {
            msg.append(String.format("%d. %s\n", index++, backup.toString()));
        }

        msg.append("\nUsage: java -jar voicemail-converter.jar --device-id <udid>");

        throw new BackupException(msg.toString(), 2);
    }
}
```

---

## Testing Backup Module

**Test file:** `src/test/java/com/voicemail/backup/BackupTest.java`

```java
package com.voicemail.backup;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class BackupTest {

    @Test
    void testBackupInfoBuilder() {
        BackupInfo info = new BackupInfo.Builder()
            .udid("1234567890abcdef1234567890abcdef12345678")
            .deviceName("Test iPhone")
            .productVersion("17.5")
            .lastBackupDate(LocalDateTime.now())
            .isEncrypted(false)
            .backupPath(Path.of("/tmp/backup"))
            .build();

        assertNotNull(info);
        assertEquals("Test iPhone", info.getDeviceName());
        assertFalse(info.isEncrypted());
    }

    @Test
    void testBackupInfoRequiredFields() {
        BackupInfo.Builder builder = new BackupInfo.Builder()
            .deviceName("Test iPhone");

        assertThrows(NullPointerException.class, builder::build);
    }

    @Test
    void testProductTypeMapping() {
        BackupInfo info = new BackupInfo.Builder()
            .udid("test")
            .productType("iPhone14,2")
            .backupPath(Path.of("/tmp"))
            .build();

        assertEquals("iPhone 13 Pro", info.getDeviceDescription());
    }

    // Note: Full tests for PlistParser, BackupValidator, and BackupDiscovery
    // would require mock backup directories and plist files
}
```

---

## Implementation Checklist

- [ ] Implement `BackupInfo` with Builder
- [ ] Implement `PlistParser`
- [ ] Implement `BackupValidator`
- [ ] Implement `BackupDiscovery`
- [ ] Write unit tests
- [ ] Test with real iOS backups
- [ ] Test encryption detection
- [ ] Test multiple backup scenarios

---

## Usage Example

```java
// In main application
BackupDiscovery discovery = new BackupDiscovery();
BackupInfo backup = discovery.discoverBackup(arguments);

System.out.println("Using backup: " + backup.getDeviceName());
System.out.println("iOS version: " + backup.getProductVersion());
System.out.println("Last backup: " + backup.getLastBackupDate());
System.out.println("Encrypted: " + backup.isEncrypted());
```

---

**End of Backup Module Implementation Guide**
