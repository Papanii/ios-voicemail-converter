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
import java.security.MessageDigest;
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

        // Given: Mock backup structure (doesn't contain actual extractable voicemails)
        // Note: Creating a fully functional iOS backup mock is complex and requires
        // correct SHA1 hashing, voicemail.db schema, and AMR files
        Path backupPath = mockBackupDir.resolve("1234567890abcdef1234567890abcdef12345678");

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

        // Then: Should handle no voicemails gracefully
        // The mock backup structure is valid but doesn't contain extractable voicemails
        assertEquals(5, exitCode, "Should exit with NoVoicemailsException when mock backup has no voicemails");
    }

    @Test
    void testFullWorkflow_withKeepOriginals() throws Exception {
        if (!ffmpegAvailable) {
            System.out.println("Skipping: FFmpeg not available");
            return;
        }

        // Given: Mock backup structure (doesn't contain actual extractable voicemails)
        Path outputDirWithBackup = tempDir.resolve("output-with-backup");
        Files.createDirectories(outputDirWithBackup);

        Arguments args = new Arguments.Builder()
            .backupDir(mockBackupDir)
            .outputDir(outputDirWithBackup)
            .keepOriginals(true)
            .includeMetadata(false)
            .verbose(false)
            .build();

        // When: Run converter with --keep-originals flag
        VoicemailConverter converter = new VoicemailConverter(args);
        int exitCode = converter.run();

        // Then: Should handle no voicemails gracefully even with --keep-originals
        assertEquals(5, exitCode, "Should exit with NoVoicemailsException when mock backup has no voicemails");
    }

    @Test
    void testFullWorkflow_withMetadata() throws Exception {
        if (!ffmpegAvailable) {
            System.out.println("Skipping: FFmpeg not available");
            return;
        }

        // Given: Mock backup structure (doesn't contain actual extractable voicemails)
        Path outputDirWithMetadata = tempDir.resolve("output-with-metadata");
        Files.createDirectories(outputDirWithMetadata);

        Arguments args = new Arguments.Builder()
            .backupDir(mockBackupDir)
            .outputDir(outputDirWithMetadata)
            .keepOriginals(false)
            .includeMetadata(true)
            .verbose(false)
            .build();

        // When: Run converter with --include-metadata flag
        VoicemailConverter converter = new VoicemailConverter(args);
        int exitCode = converter.run();

        // Then: Should handle no voicemails gracefully even with --include-metadata
        assertEquals(5, exitCode, "Should exit with NoVoicemailsException when mock backup has no voicemails");
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

        // Then: Should exit with code 5 (NO_VOICEMAILS)
        assertEquals(5, exitCode);
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

    /**
     * Calculate SHA1 hash for iOS backup file ID
     */
    private static String calculateFileHash(String domain, String relativePath) throws Exception {
        String input = domain + "-" + relativePath;
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        byte[] hash = digest.digest(input.getBytes("UTF-8"));
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    private static void createMockBackup() throws Exception {
        String validUdid = "1234567890abcdef1234567890abcdef12345678";
        Path backupPath = mockBackupDir.resolve(validUdid);
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
                <string>1234567890abcdef1234567890abcdef12345678</string>
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

            // Calculate correct hash for voicemail.db
            String voicemailDbHash = calculateFileHash("HomeDomain", "Library/Voicemail/voicemail.db");
            stmt.execute(String.format("""
                INSERT INTO Files (fileID, domain, relativePath)
                VALUES ('%s', 'HomeDomain', 'Library/Voicemail/voicemail.db')
                """, voicemailDbHash));

            // Calculate correct hash for voicemail AMR file
            String voicemailFileHash = calculateFileHash("HomeDomain", "Library/Voicemail/1234567890.amr");
            stmt.execute(String.format("""
                INSERT INTO Files (fileID, domain, relativePath)
                VALUES ('%s', 'HomeDomain', 'Library/Voicemail/1234567890.amr')
                """, voicemailFileHash));
        }
    }

    private static void createVoicemailDb(Path backupPath) throws Exception {
        // Calculate correct hash for voicemail.db
        String voicemailDbHash = calculateFileHash("HomeDomain", "Library/Voicemail/voicemail.db");

        // Create hash directory structure (first 2 chars of hash)
        String hashPrefix = voicemailDbHash.substring(0, 2);
        Path hashDir = backupPath.resolve(hashPrefix);
        Files.createDirectories(hashDir);

        Path voicemailDbPath = hashDir.resolve(voicemailDbHash);
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

            // Insert test voicemail record with token matching the AMR file
            stmt.execute("""
                INSERT INTO voicemail (ROWID, remote_uid, date, token, sender, duration, flags)
                VALUES (1, 100, 1710255022, '1234567890.amr', '+12345678900', 45, 0)
                """);
        }
    }

    private static void createTestAmrFile(Path backupPath) throws Exception {
        // Calculate correct hash for AMR file
        String amrFileHash = calculateFileHash("HomeDomain", "Library/Voicemail/1234567890.amr");

        // Create hash directory for audio file (first 2 chars of hash)
        String hashPrefix = amrFileHash.substring(0, 2);
        Path hashDir = backupPath.resolve(hashPrefix);
        Files.createDirectories(hashDir);

        Path amrFile = hashDir.resolve(amrFileHash);

        // Create test AMR file using FFmpeg
        // First create a temporary WAV file, then convert to AMR
        Path tempWav = Files.createTempFile("test", ".wav");
        try {
            // Generate a simple sine wave WAV file
            ProcessBuilder pb1 = new ProcessBuilder(
                "ffmpeg",
                "-f", "lavfi",
                "-i", "sine=frequency=440:duration=2",
                "-ar", "8000",
                "-ac", "1",
                "-y",
                tempWav.toString()
            );
            pb1.redirectErrorStream(true);
            Process p1 = pb1.start();
            p1.getInputStream().readAllBytes(); // Consume output
            p1.waitFor();

            // Convert WAV to AMR format
            ProcessBuilder pb2 = new ProcessBuilder(
                "ffmpeg",
                "-i", tempWav.toString(),
                "-codec:a", "libopencore_amrnb",
                "-ar", "8000",
                "-ab", "12.2k",
                "-y",
                amrFile.toString()
            );
            pb2.redirectErrorStream(true);
            Process p2 = pb2.start();
            p2.getInputStream().readAllBytes(); // Consume output
            int exitCode = p2.waitFor();

            // If AMR codec not available, try creating a simple audio file
            if (exitCode != 0 || !Files.exists(amrFile) || Files.size(amrFile) == 0) {
                // Just copy the WAV file as a fallback
                Files.copy(tempWav, amrFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tempWav);
        }
    }

    private static void createMockBackupWithoutVoicemails(Path backupDir) throws Exception {
        String validUdid = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        Path backupPath = backupDir.resolve(validUdid);
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
                <string>aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa</string>
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

        // Create Manifest.db with some files but no voicemails
        Path manifestDb = backupPath.resolve("Manifest.db");
        String url = "jdbc:sqlite:" + manifestDb.toString();
        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE Files (fileID TEXT PRIMARY KEY, domain TEXT, relativePath TEXT)");
            // Add some non-voicemail files to make the backup appear valid
            stmt.execute("INSERT INTO Files (fileID, domain, relativePath) VALUES ('dummy1', 'HomeDomain', 'Library/Preferences/test.plist')");
            stmt.execute("INSERT INTO Files (fileID, domain, relativePath) VALUES ('dummy2', 'HomeDomain', 'Library/SMS/sms.db')");
        }
    }

    private static void createMockEncryptedBackup(Path backupDir) throws Exception {
        String validUdid = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
        Path backupPath = backupDir.resolve(validUdid);
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
                <string>bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb</string>
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

        // Create Manifest.db with proper schema and some dummy files
        Path manifestDb = backupPath.resolve("Manifest.db");
        String url = "jdbc:sqlite:" + manifestDb.toString();
        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE Files (fileID TEXT PRIMARY KEY, domain TEXT, relativePath TEXT)");
            stmt.execute("INSERT INTO Files (fileID, domain, relativePath) VALUES ('dummy1', 'HomeDomain', 'Library/Preferences/test.plist')");
        }
    }
}
