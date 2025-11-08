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
