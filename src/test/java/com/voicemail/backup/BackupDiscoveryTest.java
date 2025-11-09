package com.voicemail.backup;

import com.voicemail.cli.Arguments;
import com.voicemail.exception.BackupException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BackupDiscovery class.
 * <p>
 * Tests backup discovery, enumeration, and selection logic.
 * </p>
 */
class BackupDiscoveryTest {

    @TempDir
    Path tempDir;

    private BackupDiscovery discovery;

    @BeforeEach
    void setUp() {
        discovery = new BackupDiscovery();
    }

    @Test
    void testDiscoverBackup_noBackups() {
        // Given: Empty backup directory
        Arguments args = new Arguments.Builder()
            .backupDir(tempDir)
            .outputDir(tempDir.resolve("output"))
            .build();

        // When/Then: Should throw exception
        BackupException exception = assertThrows(BackupException.class, () -> {
            discovery.discoverBackup(args);
        });

        assertTrue(exception.getMessage().contains("No iOS backups found"));
    }

    @Test
    void testDiscoverBackup_singleBackup() throws Exception {
        // Given: Single backup directory with Info.plist (use valid 40-char hex UDID)
        String validUdid = "1234567890abcdef1234567890abcdef12345678";
        Path backupDir = createMockBackup(tempDir, validUdid, "Test iPhone", "17.5");

        Arguments args = new Arguments.Builder()
            .backupDir(tempDir)
            .outputDir(tempDir.resolve("output"))
            .build();

        // When: Discover backup
        BackupInfo backup = discovery.discoverBackup(args);

        // Then: Should find the backup
        assertNotNull(backup);
        assertEquals(validUdid, backup.getUdid());
        assertEquals("Test iPhone", backup.getDeviceName());
        assertEquals("17.5", backup.getProductVersion());
    }

    @Test
    void testDiscoverBackup_multipleBackups_noDeviceIdSpecified() throws Exception {
        // Given: Multiple backup directories (use valid 40-char hex UDIDs)
        String udid1 = "1111111111111111111111111111111111111111";
        String udid2 = "2222222222222222222222222222222222222222";
        createMockBackup(tempDir, udid1, "iPhone 12", "17.0");
        createMockBackup(tempDir, udid2, "iPhone 13", "17.5");

        Arguments args = new Arguments.Builder()
            .backupDir(tempDir)
            .outputDir(tempDir.resolve("output"))
            .build();

        // When/Then: Should throw exception asking for device ID
        BackupException exception = assertThrows(BackupException.class, () -> {
            discovery.discoverBackup(args);
        });

        assertTrue(exception.getMessage().contains("Multiple backups found"));
        assertTrue(exception.getMessage().contains("--device-id"));
    }

    @Test
    void testDiscoverBackup_multipleBackups_deviceIdSpecified() throws Exception {
        // Given: Multiple backup directories (use valid 40-char hex UDIDs)
        String udid1 = "1111111111111111111111111111111111111111";
        String udid2 = "2222222222222222222222222222222222222222";
        createMockBackup(tempDir, udid1, "iPhone 12", "17.0");
        createMockBackup(tempDir, udid2, "iPhone 13", "17.5");

        Arguments args = new Arguments.Builder()
            .backupDir(tempDir)
            .outputDir(tempDir.resolve("output"))
            .deviceId(udid2)
            .build();

        // When: Discover backup with device ID
        BackupInfo backup = discovery.discoverBackup(args);

        // Then: Should find the correct backup
        assertNotNull(backup);
        assertEquals(udid2, backup.getUdid());
        assertEquals("iPhone 13", backup.getDeviceName());
    }

    @Test
    void testDiscoverBackup_deviceIdNotFound() throws Exception {
        // Given: Backup directory with different UDID (use valid 40-char hex UDID)
        String existingUdid = "1111111111111111111111111111111111111111";
        String nonExistentUdid = "9999999999999999999999999999999999999999";
        createMockBackup(tempDir, existingUdid, "iPhone 12", "17.0");

        Arguments args = new Arguments.Builder()
            .backupDir(tempDir)
            .outputDir(tempDir.resolve("output"))
            .deviceId(nonExistentUdid)
            .build();

        // When/Then: Should throw exception
        BackupException exception = assertThrows(BackupException.class, () -> {
            discovery.discoverBackup(args);
        });

        assertTrue(exception.getMessage().contains("No backup found for device ID"));
        assertTrue(exception.getMessage().contains(nonExistentUdid));
    }

    @Test
    void testDiscoverBackup_invalidBackupDirectory() {
        // Given: Non-existent backup directory
        Path nonExistent = tempDir.resolve("non-existent");

        Arguments args = new Arguments.Builder()
            .backupDir(nonExistent)
            .outputDir(tempDir.resolve("output"))
            .build();

        // When/Then: Should throw exception
        assertThrows(BackupException.class, () -> {
            discovery.discoverBackup(args);
        });
    }

    @Test
    void testDiscoverBackup_skipsNonBackupDirectories() throws Exception {
        // Given: Backup directory with non-backup subdirectories (use valid 40-char hex UDID)
        String validUdid = "abcdef1234567890abcdef1234567890abcdef12";
        Path backupDir = createMockBackup(tempDir, validUdid, "iPhone", "17.0");
        Files.createDirectory(tempDir.resolve("not-a-backup")); // Invalid directory name
        Files.createFile(tempDir.resolve("file.txt")); // File, not directory

        Arguments args = new Arguments.Builder()
            .backupDir(tempDir)
            .outputDir(tempDir.resolve("output"))
            .build();

        // When: Discover backup
        BackupInfo backup = discovery.discoverBackup(args);

        // Then: Should only find valid backup
        assertNotNull(backup);
        assertEquals(validUdid, backup.getUdid());
    }

    @Test
    void testDiscoverBackup_corruptedBackup() throws Exception {
        // Given: Backup directory with corrupted Info.plist (use valid 40-char hex UDID)
        String validUdid = "fedcba0987654321fedcba0987654321fedcba09";
        Path backupDir = tempDir.resolve(validUdid);
        Files.createDirectories(backupDir);
        Files.writeString(backupDir.resolve("Info.plist"), "corrupted xml content");

        Arguments args = new Arguments.Builder()
            .backupDir(tempDir)
            .outputDir(tempDir.resolve("output"))
            .build();

        // When/Then: Should skip corrupted backup
        BackupException exception = assertThrows(BackupException.class, () -> {
            discovery.discoverBackup(args);
        });

        assertTrue(exception.getMessage().contains("No iOS backups found"));
    }

    // Helper methods

    /**
     * Create a mock backup directory with minimal valid structure
     */
    private Path createMockBackup(Path parentDir, String udid, String deviceName, String version)
            throws IOException {
        Path backupDir = parentDir.resolve(udid);
        Files.createDirectories(backupDir);

        // Create minimal Info.plist
        String infoPlist = String.format("""
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
            <plist version="1.0">
            <dict>
                <key>Device Name</key>
                <string>%s</string>
                <key>Product Version</key>
                <string>%s</string>
                <key>Unique Identifier</key>
                <string>%s</string>
                <key>Last Backup Date</key>
                <date>2024-03-12T14:30:22Z</date>
            </dict>
            </plist>
            """, deviceName, version, udid);

        Files.writeString(backupDir.resolve("Info.plist"), infoPlist);

        // Create minimal Manifest.plist
        String manifestPlist = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
            <plist version="1.0">
            <dict>
                <key>IsEncrypted</key>
                <false/>
            </dict>
            </plist>
            """;

        Files.writeString(backupDir.resolve("Manifest.plist"), manifestPlist);

        // Create Manifest.db with proper schema (required for validation)
        Path manifestDb = backupDir.resolve("Manifest.db");
        String url = "jdbc:sqlite:" + manifestDb.toString();
        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement()) {
            // Create Files table matching iOS backup schema
            stmt.execute("""
                CREATE TABLE Files (
                    fileID TEXT PRIMARY KEY,
                    domain TEXT,
                    relativePath TEXT,
                    file BLOB
                )
                """);
            // Insert a test row so validation can verify database structure
            stmt.execute("INSERT INTO Files (fileID, domain, relativePath) " +
                        "VALUES ('test-file-id', 'HomeDomain', 'Library/test.db')");
        } catch (Exception e) {
            throw new IOException("Failed to create mock Manifest.db", e);
        }

        return backupDir;
    }
}
