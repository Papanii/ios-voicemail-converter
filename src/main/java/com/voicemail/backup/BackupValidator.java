package com.voicemail.backup;

import com.voicemail.exception.BackupException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
