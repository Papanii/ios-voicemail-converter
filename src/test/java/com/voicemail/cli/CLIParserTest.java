package com.voicemail.cli;

import com.voicemail.exception.ConfigurationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CLIParserTest {
    private CLIParser parser;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        parser = new CLIParser();
    }

    @Test
    void testParseMinimalArguments() throws Exception {
        // Create temp directory for testing
        Path tempBackup = Files.createTempDirectory(tempDir, "backup");

        String[] args = {"--backup-dir", tempBackup.toString()};
        Arguments result = parser.parse(args);

        assertNotNull(result);
        assertEquals(tempBackup.toAbsolutePath(), result.getBackupDir());
        assertTrue(result.getOutputDir().toString().contains("voicemail-wavs"));
    }

    @Test
    void testParseAllArguments() throws Exception {
        Path tempBackup = Files.createTempDirectory(tempDir, "backup");
        Path tempOutput = tempDir.resolve("output");

        String[] args = {
            "--backup-dir", tempBackup.toString(),
            "--output-dir", tempOutput.toString(),
            "--device-id", "1234567890123456789012345678901234567890",
            "--backup-password", "secret",
            "--format", "wav",
            "--keep-originals",
            "--include-metadata",
            "--verbose"
        };

        Arguments result = parser.parse(args);

        assertNotNull(result);
        assertEquals(tempBackup.toAbsolutePath(), result.getBackupDir());
        assertEquals(tempOutput.toAbsolutePath(), result.getOutputDir());
        assertTrue(result.getDeviceId().isPresent());
        assertEquals("1234567890123456789012345678901234567890", result.getDeviceId().get());
        assertTrue(result.getBackupPassword().isPresent());
        assertEquals("secret", result.getBackupPassword().get());
        assertEquals("wav", result.getFormat());
        assertTrue(result.isKeepOriginals());
        assertTrue(result.isIncludeMetadata());
        assertTrue(result.isVerbose());
    }

    @Test
    void testInvalidBackupDirectory() {
        String[] args = {"--backup-dir", "/path/that/does/not/exist"};

        ConfigurationException ex = assertThrows(
            ConfigurationException.class,
            () -> parser.parse(args)
        );
        assertTrue(ex.getMessage().contains("does not exist"));
        assertEquals(2, ex.getExitCode());
        assertTrue(ex.hasSuggestion());
    }

    @Test
    void testBackupDirectoryIsFile() throws Exception {
        Path file = Files.createTempFile(tempDir, "file", ".txt");

        String[] args = {"--backup-dir", file.toString()};

        ConfigurationException ex = assertThrows(
            ConfigurationException.class,
            () -> parser.parse(args)
        );
        assertTrue(ex.getMessage().contains("not a directory"));
    }

    @Test
    void testInvalidUdid() throws Exception {
        Path tempBackup = Files.createTempDirectory(tempDir, "backup");

        String[] args = {
            "--backup-dir", tempBackup.toString(),
            "--device-id", "invalid-udid"
        };

        ConfigurationException ex = assertThrows(
            ConfigurationException.class,
            () -> parser.parse(args)
        );
        assertTrue(ex.getMessage().contains("Invalid device ID format"));
    }

    @Test
    void testValidUdidHexFormat() throws Exception {
        Path tempBackup = Files.createTempDirectory(tempDir, "backup");

        String validHex = "1234567890abcdef1234567890abcdef12345678";
        String[] args = {
            "--backup-dir", tempBackup.toString(),
            "--device-id", validHex
        };

        Arguments result = parser.parse(args);
        assertTrue(result.getDeviceId().isPresent());
        assertEquals(validHex, result.getDeviceId().get());
    }

    @Test
    void testValidUdidUuidFormat() throws Exception {
        Path tempBackup = Files.createTempDirectory(tempDir, "backup");

        String validUuid = "12345678-1234-1234-1234-123456789012";
        String[] args = {
            "--backup-dir", tempBackup.toString(),
            "--device-id", validUuid
        };

        Arguments result = parser.parse(args);
        assertTrue(result.getDeviceId().isPresent());
        assertEquals(validUuid, result.getDeviceId().get());
    }

    @Test
    void testUnsupportedFormat() throws Exception {
        Path tempBackup = Files.createTempDirectory(tempDir, "backup");

        String[] args = {
            "--backup-dir", tempBackup.toString(),
            "--format", "mp3"
        };

        ConfigurationException ex = assertThrows(
            ConfigurationException.class,
            () -> parser.parse(args)
        );
        assertTrue(ex.getMessage().contains("not yet supported"));
        assertTrue(ex.hasSuggestion());
    }

    @Test
    void testHelpFlag() throws Exception {
        // Capture stdout
        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(outContent));

        try {
            String[] args = {"--help"};
            Arguments result = parser.parse(args);

            assertNull(result); // Help flag returns null
            String output = outContent.toString();
            assertTrue(output.contains("iOS Voicemail Converter"));
            assertTrue(output.contains("USAGE:"));
        } finally {
            System.setOut(originalOut);
        }
    }

    @Test
    void testVersionFlag() throws Exception {
        // Capture stdout
        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(outContent));

        try {
            String[] args = {"--version"};
            Arguments result = parser.parse(args);

            assertNull(result); // Version flag returns null
            String output = outContent.toString();
            assertTrue(output.contains("v1.0.0"));
            assertTrue(output.contains("Java Version:"));
        } finally {
            System.setOut(originalOut);
        }
    }

    @Test
    void testShortOptions() throws Exception {
        Path tempBackup = Files.createTempDirectory(tempDir, "backup");
        Path tempOutput = tempDir.resolve("output");

        String[] args = {
            "-b", tempBackup.toString(),
            "-o", tempOutput.toString(),
            "-v"
        };

        Arguments result = parser.parse(args);
        assertNotNull(result);
        assertEquals(tempBackup.toAbsolutePath(), result.getBackupDir());
        assertEquals(tempOutput.toAbsolutePath(), result.getOutputDir());
        assertTrue(result.isVerbose());
    }

    @Test
    void testDefaultOutputDirectory() throws Exception {
        Path tempBackup = Files.createTempDirectory(tempDir, "backup");

        String[] args = {"--backup-dir", tempBackup.toString()};
        Arguments result = parser.parse(args);

        assertNotNull(result.getOutputDir());
        assertTrue(result.getOutputDir().toString().contains("voicemail-wavs"));
    }

    @Test
    void testDefaultFormat() throws Exception {
        Path tempBackup = Files.createTempDirectory(tempDir, "backup");

        String[] args = {"--backup-dir", tempBackup.toString()};
        Arguments result = parser.parse(args);

        assertEquals("wav", result.getFormat());
    }

    @Test
    void testKeepOriginalsFlag() throws Exception {
        Path tempBackup = Files.createTempDirectory(tempDir, "backup");

        String[] args = {
            "--backup-dir", tempBackup.toString(),
            "--keep-originals"
        };

        Arguments result = parser.parse(args);
        assertTrue(result.isKeepOriginals());
    }

    @Test
    void testIncludeMetadataFlag() throws Exception {
        Path tempBackup = Files.createTempDirectory(tempDir, "backup");

        String[] args = {
            "--backup-dir", tempBackup.toString(),
            "--include-metadata"
        };

        Arguments result = parser.parse(args);
        assertTrue(result.isIncludeMetadata());
    }

    @Test
    void testLogFileOption() throws Exception {
        Path tempBackup = Files.createTempDirectory(tempDir, "backup");
        Path logFile = tempDir.resolve("app.log");

        String[] args = {
            "--backup-dir", tempBackup.toString(),
            "--log-file", logFile.toString()
        };

        Arguments result = parser.parse(args);
        assertTrue(result.getLogFile().isPresent());
        assertEquals(logFile.toAbsolutePath(), result.getLogFile().get());
    }

    @Test
    void testOutputDirectoryExistsAsFile() throws Exception {
        Path tempBackup = Files.createTempDirectory(tempDir, "backup");
        Path file = Files.createTempFile(tempDir, "output", ".txt");

        String[] args = {
            "--backup-dir", tempBackup.toString(),
            "--output-dir", file.toString()
        };

        ConfigurationException ex = assertThrows(
            ConfigurationException.class,
            () -> parser.parse(args)
        );
        assertTrue(ex.getMessage().contains("not a directory"));
    }

    @Test
    void testLogFileIsDirectory() throws Exception {
        Path tempBackup = Files.createTempDirectory(tempDir, "backup");
        Path dir = Files.createTempDirectory(tempDir, "logdir");

        String[] args = {
            "--backup-dir", tempBackup.toString(),
            "--log-file", dir.toString()
        };

        ConfigurationException ex = assertThrows(
            ConfigurationException.class,
            () -> parser.parse(args)
        );
        assertTrue(ex.getMessage().contains("is a directory"));
    }

    @Test
    void testFormatIsCaseInsensitive() throws Exception {
        Path tempBackup = Files.createTempDirectory(tempDir, "backup");

        String[] args = {
            "--backup-dir", tempBackup.toString(),
            "--format", "WAV"
        };

        Arguments result = parser.parse(args);
        assertEquals("wav", result.getFormat());
    }

    @Test
    void testInvalidArgumentThrowsConfigurationException() throws Exception {
        Path tempBackup = Files.createTempDirectory(tempDir, "backup");

        String[] args = {
            "--backup-dir", tempBackup.toString(),
            "--invalid-option"
        };

        assertThrows(ConfigurationException.class, () -> parser.parse(args));
    }

    @Test
    void testPrintHelpDoesNotThrow() {
        assertDoesNotThrow(() -> parser.printHelp());
    }

    @Test
    void testPrintVersionDoesNotThrow() {
        assertDoesNotThrow(() -> parser.printVersion());
    }
}
