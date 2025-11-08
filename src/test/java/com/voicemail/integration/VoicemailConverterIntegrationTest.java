package com.voicemail.integration;

import com.voicemail.VoicemailConverter;
import com.voicemail.cli.Arguments;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end integration tests for the complete voicemail conversion workflow.
 * <p>
 * Tests the entire pipeline:
 * 1. Backup discovery
 * 2. Voicemail extraction
 * 3. Metadata processing
 * 4. Audio conversion (requires FFmpeg)
 * 5. File organization
 * </p>
 */
class VoicemailConverterIntegrationTest {

    @TempDir
    static Path tempDir;

    private static Path mockBackupDir;
    private static Path outputDir;
    private static boolean ffmpegAvailable = false;

    @BeforeAll
    static void setUp() throws Exception {
        // Check if FFmpeg is available
        try {
            ProcessBuilder pb = new ProcessBuilder("ffmpeg", "-version");
            Process process = pb.start();
            int exitCode = process.waitFor();
            ffmpegAvailable = (exitCode == 0);
        } catch (Exception e) {
            ffmpegAvailable = false;
        }

        if (!ffmpegAvailable) {
            System.out.println("WARNING: FFmpeg not found, integration tests will be limited");
            System.out.println("To run full tests, install FFmpeg: brew install ffmpeg");
            return;
        }

        // Create mock backup structure
        mockBackupDir = tempDir.resolve("backups");
        outputDir = tempDir.resolve("output");
        Files.createDirectories(mockBackupDir);
        Files.createDirectories(outputDir);

        createMockBackup();
    }

    @Test
    void testFullWorkflow_withMockBackup() throws Exception {
        if (!ffmpegAvailable) {
            System.out.println("Skipping: FFmpeg not available");
            return;
        }

        // Given: Mock backup with voicemail
        Path backupPath = mockBackupDir.resolve("test-udid-123");

        Arguments args = new Arguments.Builder()
            .backupDir(mockBackupDir)
            .outputDir(outputDir)
            .keepOriginals(false)
            .includeMetadata(false)
            .verbose(false)
            .build();

        // When: Run converter
        VoicemailConverter converter = new VoicemailConverter(args);
        int exitCode = converter.run();

        // Then: Should complete successfully
        assertEquals(0, exitCode);

        // Verify output files were created
        assertTrue(Files.exists(outputDir));
        long fileCount = Files.walk(outputDir)
            .filter(Files::isRegularFile)
            .filter(p -> p.toString().endsWith(".wav"))
            .count();
        assertTrue(fileCount > 0, "Should have created at least one WAV file");
    }

    @Test
    void testFullWorkflow_withKeepOriginals() throws Exception {
        if (!ffmpegAvailable) {
            System.out.println("Skipping: FFmpeg not available");
            return;
        }

        // Given: Mock backup with voicemail
        Path outputDirWithBackup = tempDir.resolve("output-with-backup");
        Files.createDirectories(outputDirWithBackup);

        Arguments args = new Arguments.Builder()
            .backupDir(mockBackupDir)
            .outputDir(outputDirWithBackup)
            .keepOriginals(true)
            .includeMetadata(false)
            .verbose(false)
            .build();

        // When: Run converter with --keep-originals
        VoicemailConverter converter = new VoicemailConverter(args);
        int exitCode = converter.run();

        // Then: Should complete successfully
        assertEquals(0, exitCode);

        // Verify backup directory was created
        Path backupDir = outputDirWithBackup.getParent().resolve("voicemail-backup");
        assertTrue(Files.exists(backupDir), "Backup directory should exist");

        // Verify original files were copied
        long originalFileCount = Files.walk(backupDir)
            .filter(Files::isRegularFile)
            .filter(p -> p.toString().endsWith(".amr"))
            .count();
        assertTrue(originalFileCount > 0, "Should have copied original AMR files");
    }

    @Test
    void testFullWorkflow_withMetadata() throws Exception {
        if (!ffmpegAvailable) {
            System.out.println("Skipping: FFmpeg not available");
            return;
        }

        // Given: Mock backup with voicemail
        Path outputDirWithMetadata = tempDir.resolve("output-with-metadata");
        Files.createDirectories(outputDirWithMetadata);

        Arguments args = new Arguments.Builder()
            .backupDir(mockBackupDir)
            .outputDir(outputDirWithMetadata)
            .keepOriginals(false)
            .includeMetadata(true)
            .verbose(false)
            .build();

        // When: Run converter with --include-metadata
        VoicemailConverter converter = new VoicemailConverter(args);
        int exitCode = converter.run();

        // Then: Should complete successfully
        assertEquals(0, exitCode);

        // Verify JSON metadata files were created
        long jsonFileCount = Files.walk(outputDirWithMetadata)
            .filter(Files::isRegularFile)
            .filter(p -> p.toString().endsWith(".json"))
            .count();
        assertTrue(jsonFileCount > 0, "Should have created JSON metadata files");
    }

    @Test
    void testFullWorkflow_noVoicemails() throws Exception {
        // Given: Backup with no voicemails
        Path emptyBackupDir = tempDir.resolve("empty-backup");
        Files.createDirectories(emptyBackupDir);
        createMockBackupWithoutVoicemails(emptyBackupDir);

        Path emptyOutputDir = tempDir.resolve("empty-output");
        Files.createDirectories(emptyOutputDir);

        Arguments args = new Arguments.Builder()
            .backupDir(emptyBackupDir)
            .outputDir(emptyOutputDir)
            .build();

        // When: Run converter
        VoicemailConverter converter = new VoicemailConverter(args);
        int exitCode = converter.run();

        // Then: Should exit with code 2 (NO_VOICEMAILS)
        assertEquals(2, exitCode);
    }

    @Test
    void testFullWorkflow_encryptedBackup() throws Exception {
        // Given: Encrypted backup
        Path encryptedBackupDir = tempDir.resolve("encrypted-backup");
        Files.createDirectories(encryptedBackupDir);
        createMockEncryptedBackup(encryptedBackupDir);

        Path encryptedOutputDir = tempDir.resolve("encrypted-output");
        Files.createDirectories(encryptedOutputDir);

        Arguments args = new Arguments.Builder()
            .backupDir(encryptedBackupDir)
            .outputDir(encryptedOutputDir)
            .build();

        // When: Run converter
        VoicemailConverter converter = new VoicemailConverter(args);
        int exitCode = converter.run();

        // Then: Should exit with error code (encrypted not supported)
        assertNotEquals(0, exitCode);
    }

    // Helper methods to create mock backup structures

    private static void createMockBackup() throws Exception {
        Path backupPath = mockBackupDir.resolve("test-udid-123");
        Files.createDirectories(backupPath);

        // Create Info.plist
        String infoPlist = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
            <plist version="1.0">
            <dict>
                <key>Device Name</key>
                <string>Test iPhone</string>
                <key>Product Version</key>
                <string>17.5</string>
                <key>Unique Identifier</key>
                <string>test-udid-123</string>
                <key>Last Backup Date</key>
                <date>2024-03-12T14:30:22Z</date>
            </dict>
            </plist>
            """;
        Files.writeString(backupPath.resolve("Info.plist"), infoPlist);

        // Create Manifest.plist
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
        Files.writeString(backupPath.resolve("Manifest.plist"), manifestPlist);

        // Create Manifest.db
        createManifestDb(backupPath);

        // Create voicemail.db
        createVoicemailDb(backupPath);

        // Create test AMR file
        createTestAmrFile(backupPath);
    }

    private static void createManifestDb(Path backupPath) throws Exception {
        Path manifestDb = backupPath.resolve("Manifest.db");
        String url = "jdbc:sqlite:" + manifestDb.toString();

        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement()) {

            // Create Files table
            stmt.execute("""
                CREATE TABLE Files (
                    fileID TEXT PRIMARY KEY,
                    domain TEXT,
                    relativePath TEXT,
                    file BLOB
                )
                """);

            // Insert voicemail.db entry
            String voicemailDbHash = "992df473bbb9e132f4b3b6e4d33f72171e97bc7a"; // Example hash
            stmt.execute(String.format("""
                INSERT INTO Files (fileID, domain, relativePath)
                VALUES ('%s', 'HomeDomain', 'Library/Voicemail/voicemail.db')
                """, voicemailDbHash));

            // Insert test voicemail file entry
            String voicemailFileHash = "abc123def456789012345678901234567890abcd"; // Example hash
            stmt.execute(String.format("""
                INSERT INTO Files (fileID, domain, relativePath)
                VALUES ('%s', 'HomeDomain', 'Library/Voicemail/1234567890.amr')
                """, voicemailFileHash));
        }
    }

    private static void createVoicemailDb(Path backupPath) throws Exception {
        // Create a hash directory structure
        Path hashDir = backupPath.resolve("99");
        Files.createDirectories(hashDir);

        Path voicemailDbPath = hashDir.resolve("992df473bbb9e132f4b3b6e4d33f72171e97bc7a");
        String url = "jdbc:sqlite:" + voicemailDbPath.toString();

        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement()) {

            // Create voicemail table
            stmt.execute("""
                CREATE TABLE voicemail (
                    ROWID INTEGER PRIMARY KEY,
                    remote_uid INTEGER,
                    date INTEGER,
                    token TEXT,
                    sender TEXT,
                    callback_num TEXT,
                    duration INTEGER,
                    expiration INTEGER,
                    trashed_date INTEGER,
                    flags INTEGER
                )
                """);

            // Insert test voicemail record
            stmt.execute("""
                INSERT INTO voicemail (ROWID, remote_uid, date, sender, duration, flags)
                VALUES (1, 100, 1710255022, '+12345678900', 45, 0)
                """);
        }
    }

    private static void createTestAmrFile(Path backupPath) throws Exception {
        // Create hash directory for audio file
        Path hashDir = backupPath.resolve("ab");
        Files.createDirectories(hashDir);

        Path amrFile = hashDir.resolve("abc123def456789012345678901234567890abcd");

        // Create test AMR file using FFmpeg
        ProcessBuilder pb = new ProcessBuilder(
            "ffmpeg",
            "-f", "lavfi",
            "-i", "sine=frequency=1000:duration=5",
            "-ar", "8000",
            "-ac", "1",
            "-ab", "12.2k",
            "-y",
            amrFile.toString()
        );
        pb.redirectErrorStream(true);
        Process process = pb.start();
        process.waitFor();
    }

    private static void createMockBackupWithoutVoicemails(Path backupDir) throws Exception {
        Path backupPath = backupDir.resolve("empty-udid");
        Files.createDirectories(backupPath);

        // Create minimal Info.plist
        String infoPlist = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
            <plist version="1.0">
            <dict>
                <key>Device Name</key>
                <string>Empty iPhone</string>
                <key>Product Version</key>
                <string>17.0</string>
                <key>Unique Identifier</key>
                <string>empty-udid</string>
            </dict>
            </plist>
            """;
        Files.writeString(backupPath.resolve("Info.plist"), infoPlist);

        // Create Manifest.plist
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
        Files.writeString(backupPath.resolve("Manifest.plist"), manifestPlist);

        // Create empty Manifest.db
        Path manifestDb = backupPath.resolve("Manifest.db");
        String url = "jdbc:sqlite:" + manifestDb.toString();
        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE Files (fileID TEXT PRIMARY KEY, domain TEXT, relativePath TEXT)");
        }
    }

    private static void createMockEncryptedBackup(Path backupDir) throws Exception {
        Path backupPath = backupDir.resolve("encrypted-udid");
        Files.createDirectories(backupPath);

        // Create Info.plist
        String infoPlist = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
            <plist version="1.0">
            <dict>
                <key>Device Name</key>
                <string>Encrypted iPhone</string>
                <key>Product Version</key>
                <string>17.0</string>
                <key>Unique Identifier</key>
                <string>encrypted-udid</string>
            </dict>
            </plist>
            """;
        Files.writeString(backupPath.resolve("Info.plist"), infoPlist);

        // Create Manifest.plist indicating encrypted
        String manifestPlist = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
            <plist version="1.0">
            <dict>
                <key>IsEncrypted</key>
                <true/>
            </dict>
            </plist>
            """;
        Files.writeString(backupPath.resolve("Manifest.plist"), manifestPlist);

        // Create Manifest.db
        Path manifestDb = backupPath.resolve("Manifest.db");
        Files.createFile(manifestDb);
    }
}
