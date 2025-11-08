package com.voicemail.output;

import com.voicemail.extractor.VoicemailFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for FileOrganizer class.
 * <p>
 * Tests the complete file organization workflow including:
 * - Creating date-based directories
 * - Generating unique filenames
 * - Copying WAV, JSON, and original files
 * - Handling collisions
 * - Error handling
 * </p>
 */
class FileOrganizerIntegrationTest {

    @TempDir
    Path tempDir;

    private FileOrganizer organizer;

    @BeforeEach
    void setUp() {
        organizer = new FileOrganizer();
    }

    @Test
    void testOrganizeFiles_singleFile() throws Exception {
        // Given: Single voicemail file
        Path wavFile = createTestWavFile("test.wav");
        Path jsonFile = createTestJsonFile("test.json");
        Path originalFile = createTestAmrFile("test.amr");

        VoicemailFile.VoicemailMetadata metadata = createTestMetadata(
            Instant.parse("2024-03-12T14:30:22Z"),
            "+12345678900",
            45
        );

        VoicemailFile vmFile = new VoicemailFile.Builder()
            .fileId("test123")
            .relativePath("voicemail/test.amr")
            .backupFilePath(originalFile)
            .extractedPath(originalFile)
            .metadata(metadata)
            .build();

        List<FileOrganizer.FileToOrganize> files = List.of(
            new FileOrganizer.FileToOrganize(wavFile, jsonFile, originalFile, vmFile)
        );

        Path outputDir = tempDir.resolve("output");
        Path backupDir = tempDir.resolve("backup");

        // When: Organize files
        OutputResult result = organizer.organizeFiles(files, outputDir, backupDir);

        // Then: Should succeed
        assertEquals(1, result.getTotalFiles());
        assertEquals(1, result.getSuccessfulFiles());
        assertEquals(0, result.getFailedFiles());
        assertTrue(result.allSucceeded());

        // Verify directory structure
        assertTrue(Files.exists(outputDir.resolve("2024-03-12")));
        assertTrue(Files.exists(backupDir.resolve("2024-03-12")));

        // Verify files were copied
        assertTrue(Files.walk(outputDir)
            .anyMatch(p -> p.toString().endsWith(".wav")));
        assertTrue(Files.walk(outputDir)
            .anyMatch(p -> p.toString().endsWith(".json")));
        assertTrue(Files.walk(backupDir)
            .anyMatch(p -> p.toString().endsWith(".amr")));
    }

    @Test
    void testOrganizeFiles_multipleFiles() throws Exception {
        // Given: Multiple voicemail files from different days
        List<FileOrganizer.FileToOrganize> files = new ArrayList<>();

        // File 1: March 12
        files.add(createFileToOrganize(
            "file1", Instant.parse("2024-03-12T14:30:22Z"), "+11111111111"
        ));

        // File 2: March 12 (same day, different time)
        files.add(createFileToOrganize(
            "file2", Instant.parse("2024-03-12T18:45:00Z"), "+12222222222"
        ));

        // File 3: March 13 (different day)
        files.add(createFileToOrganize(
            "file3", Instant.parse("2024-03-13T09:15:00Z"), "+13333333333"
        ));

        Path outputDir = tempDir.resolve("output");
        Path backupDir = tempDir.resolve("backup");

        // When: Organize files
        OutputResult result = organizer.organizeFiles(files, outputDir, backupDir);

        // Then: Should succeed for all
        assertEquals(3, result.getTotalFiles());
        assertEquals(3, result.getSuccessfulFiles());
        assertEquals(0, result.getFailedFiles());

        // Verify date directories
        assertTrue(Files.exists(outputDir.resolve("2024-03-12")));
        assertTrue(Files.exists(outputDir.resolve("2024-03-13")));
        assertTrue(Files.exists(backupDir.resolve("2024-03-12")));
        assertTrue(Files.exists(backupDir.resolve("2024-03-13")));

        // Count files in each directory
        long march12Files = Files.list(outputDir.resolve("2024-03-12"))
            .filter(p -> p.toString().endsWith(".wav"))
            .count();
        long march13Files = Files.list(outputDir.resolve("2024-03-13"))
            .filter(p -> p.toString().endsWith(".wav"))
            .count();

        assertEquals(2, march12Files);
        assertEquals(1, march13Files);
    }

    @Test
    void testOrganizeFiles_noBackupDir() throws Exception {
        // Given: Single voicemail file, no backup directory
        Path wavFile = createTestWavFile("test.wav");
        Path jsonFile = createTestJsonFile("test.json");
        Path originalFile = createTestAmrFile("test.amr");

        VoicemailFile.VoicemailMetadata metadata = createTestMetadata(
            Instant.parse("2024-03-12T14:30:22Z"),
            "+12345678900",
            45
        );

        VoicemailFile vmFile = new VoicemailFile.Builder()
            .fileId("test123")
            .relativePath("voicemail/test.amr")
            .backupFilePath(originalFile)
            .extractedPath(originalFile)
            .metadata(metadata)
            .build();

        List<FileOrganizer.FileToOrganize> files = List.of(
            new FileOrganizer.FileToOrganize(wavFile, jsonFile, originalFile, vmFile)
        );

        Path outputDir = tempDir.resolve("output");

        // When: Organize files without backup directory
        OutputResult result = organizer.organizeFiles(files, outputDir, null);

        // Then: Should succeed, but not copy originals
        assertEquals(1, result.getSuccessfulFiles());
        assertTrue(Files.exists(outputDir.resolve("2024-03-12")));

        // Verify WAV and JSON copied
        assertTrue(Files.walk(outputDir)
            .anyMatch(p -> p.toString().endsWith(".wav")));
        assertTrue(Files.walk(outputDir)
            .anyMatch(p -> p.toString().endsWith(".json")));
    }

    @Test
    void testOrganizeFiles_filenameCollision() throws Exception {
        // Given: Two files with same metadata (same time, same caller)
        Instant sameTime = Instant.parse("2024-03-12T14:30:22Z");
        String sameCaller = "+12345678900";

        List<FileOrganizer.FileToOrganize> files = List.of(
            createFileToOrganize("file1", sameTime, sameCaller),
            createFileToOrganize("file2", sameTime, sameCaller)
        );

        Path outputDir = tempDir.resolve("output");

        // When: Organize files
        OutputResult result = organizer.organizeFiles(files, outputDir, null);

        // Then: Should handle collision with unique names
        assertEquals(2, result.getSuccessfulFiles());

        // Verify both files exist with different names
        List<Path> wavFiles = Files.walk(outputDir)
            .filter(p -> p.toString().endsWith(".wav"))
            .toList();
        assertEquals(2, wavFiles.size());

        // One should have base name, other should have -1 suffix
        boolean hasBase = wavFiles.stream().anyMatch(p -> !p.getFileName().toString().matches(".*-\\d+\\.wav"));
        boolean hasSuffix = wavFiles.stream().anyMatch(p -> p.getFileName().toString().matches(".*-\\d+\\.wav"));
        assertTrue(hasBase && hasSuffix);
    }

    @Test
    void testOrganizeFiles_partialFailure() throws Exception {
        // Given: Mix of valid and invalid files
        Path validWav = createTestWavFile("valid.wav");
        Path validJson = createTestJsonFile("valid.json");
        Path validOriginal = createTestAmrFile("valid.amr");

        Path invalidWav = tempDir.resolve("non-existent.wav"); // Does not exist

        VoicemailFile.VoicemailMetadata metadata = createTestMetadata(
            Instant.parse("2024-03-12T14:30:22Z"),
            "+12345678900",
            45
        );

        VoicemailFile validFile = new VoicemailFile.Builder()
            .fileId("valid")
            .relativePath("voicemail/valid.amr")
            .backupFilePath(validOriginal)
            .extractedPath(validOriginal)
            .metadata(metadata)
            .build();

        VoicemailFile invalidFile = new VoicemailFile.Builder()
            .fileId("invalid")
            .relativePath("voicemail/invalid.amr")
            .backupFilePath(invalidWav) // Non-existent
            .extractedPath(invalidWav)
            .metadata(metadata)
            .build();

        List<FileOrganizer.FileToOrganize> files = List.of(
            new FileOrganizer.FileToOrganize(validWav, validJson, validOriginal, validFile),
            new FileOrganizer.FileToOrganize(invalidWav, validJson, invalidWav, invalidFile)
        );

        Path outputDir = tempDir.resolve("output");

        // When: Organize files
        OutputResult result = organizer.organizeFiles(files, outputDir, null);

        // Then: Should have partial success
        assertEquals(2, result.getTotalFiles());
        assertEquals(1, result.getSuccessfulFiles());
        assertEquals(1, result.getFailedFiles());
        assertFalse(result.allSucceeded());
        assertTrue(result.hasErrors());
        assertEquals(1, result.getErrors().size());
    }

    @Test
    void testOrganizeFiles_specialCharactersInFilename() throws Exception {
        // Given: Voicemail with special characters in caller name
        Path wavFile = createTestWavFile("special.wav");
        Path jsonFile = createTestJsonFile("special.json");
        Path originalFile = createTestAmrFile("special.amr");

        VoicemailFile.VoicemailMetadata metadata = new VoicemailFile.VoicemailMetadata(
            1, 100,
            Instant.parse("2024-03-12T14:30:22Z"),
            "+12345678900",
            null,
            45,
            null,
            null,
            0
        );

        VoicemailFile vmFile = new VoicemailFile.Builder()
            .fileId("special")
            .relativePath("voicemail/special.amr")
            .backupFilePath(originalFile)
            .extractedPath(originalFile)
            .metadata(metadata)
            .build();

        List<FileOrganizer.FileToOrganize> files = List.of(
            new FileOrganizer.FileToOrganize(wavFile, jsonFile, originalFile, vmFile)
        );

        Path outputDir = tempDir.resolve("output");

        // When: Organize files
        OutputResult result = organizer.organizeFiles(files, outputDir, null);

        // Then: Should sanitize filename
        assertEquals(1, result.getSuccessfulFiles());

        // Verify filename doesn't contain unsafe characters
        List<Path> wavFiles = Files.walk(outputDir)
            .filter(p -> p.toString().endsWith(".wav"))
            .toList();
        assertEquals(1, wavFiles.size());

        String filename = wavFiles.get(0).getFileName().toString();
        assertFalse(filename.contains("/"));
        assertFalse(filename.contains("\\"));
        assertFalse(filename.contains("<"));
        assertFalse(filename.contains(">"));
    }

    @Test
    void testOrganizeFiles_unknownCaller() throws Exception {
        // Given: Voicemail with unknown caller
        Path wavFile = createTestWavFile("unknown.wav");
        Path jsonFile = createTestJsonFile("unknown.json");
        Path originalFile = createTestAmrFile("unknown.amr");

        VoicemailFile.VoicemailMetadata metadata = new VoicemailFile.VoicemailMetadata(
            1, 100,
            Instant.parse("2024-03-12T14:30:22Z"),
            "Unknown",
            null,
            45,
            null,
            null,
            0
        );

        VoicemailFile vmFile = new VoicemailFile.Builder()
            .fileId("unknown")
            .relativePath("voicemail/unknown.amr")
            .backupFilePath(originalFile)
            .extractedPath(originalFile)
            .metadata(metadata)
            .build();

        List<FileOrganizer.FileToOrganize> files = List.of(
            new FileOrganizer.FileToOrganize(wavFile, jsonFile, originalFile, vmFile)
        );

        Path outputDir = tempDir.resolve("output");

        // When: Organize files
        OutputResult result = organizer.organizeFiles(files, outputDir, null);

        // Then: Should use "Unknown" in filename
        assertEquals(1, result.getSuccessfulFiles());

        List<Path> wavFiles = Files.walk(outputDir)
            .filter(p -> p.toString().endsWith(".wav"))
            .toList();
        assertEquals(1, wavFiles.size());

        String filename = wavFiles.get(0).getFileName().toString();
        assertTrue(filename.contains("Unknown"));
    }

    // Helper methods

    private Path createTestWavFile(String name) throws Exception {
        Path file = tempDir.resolve(name);
        Files.writeString(file, "fake wav content");
        return file;
    }

    private Path createTestJsonFile(String name) throws Exception {
        Path file = tempDir.resolve(name);
        Files.writeString(file, "{\"test\": \"data\"}");
        return file;
    }

    private Path createTestAmrFile(String name) throws Exception {
        Path file = tempDir.resolve(name);
        Files.writeString(file, "fake amr content");
        return file;
    }

    private VoicemailFile.VoicemailMetadata createTestMetadata(Instant time, String caller, int duration) {
        return new VoicemailFile.VoicemailMetadata(
            1, 100, time, caller, null, duration, null, null, 0
        );
    }

    private FileOrganizer.FileToOrganize createFileToOrganize(
            String id, Instant time, String caller) throws Exception {

        Path wavFile = createTestWavFile(id + ".wav");
        Path jsonFile = createTestJsonFile(id + ".json");
        Path originalFile = createTestAmrFile(id + ".amr");

        VoicemailFile.VoicemailMetadata metadata = createTestMetadata(time, caller, 45);

        VoicemailFile vmFile = new VoicemailFile.Builder()
            .fileId(id)
            .relativePath("voicemail/" + id + ".amr")
            .backupFilePath(originalFile)
            .extractedPath(originalFile)
            .metadata(metadata)
            .build();

        return new FileOrganizer.FileToOrganize(wavFile, jsonFile, originalFile, vmFile);
    }
}
