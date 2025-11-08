package com.voicemail.output;

import com.voicemail.extractor.VoicemailFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class OutputTest {

    @TempDir
    Path tempDir;

    private FilenameGenerator filenameGenerator;
    private DirectoryCreator directoryCreator;

    @BeforeEach
    void setUp() {
        filenameGenerator = new FilenameGenerator();
        directoryCreator = new DirectoryCreator();
    }

    @Test
    void testOutputResultBuilder() {
        OutputResult result = new OutputResult.Builder()
            .totalFiles(10)
            .successfulFiles(8)
            .failedFiles(2)
            .duration(Duration.ofSeconds(30))
            .build();

        assertEquals(10, result.getTotalFiles());
        assertEquals(8, result.getSuccessfulFiles());
        assertEquals(2, result.getFailedFiles());
        assertEquals(Duration.ofSeconds(30), result.getDuration());
        assertFalse(result.allSucceeded());
        assertTrue(result.getOrganizedFiles().isEmpty());
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    void testOutputResultAllSucceeded() {
        OutputResult result = new OutputResult.Builder()
            .totalFiles(5)
            .successfulFiles(5)
            .failedFiles(0)
            .build();

        assertTrue(result.allSucceeded());
        assertFalse(result.hasErrors());
    }

    @Test
    void testOrganizedFile() {
        Path wavFile = tempDir.resolve("test.wav");
        Path jsonFile = tempDir.resolve("test.json");
        Path originalFile = tempDir.resolve("test.amr");

        OutputResult.OrganizedFile organized = new OutputResult.OrganizedFile(
            wavFile, jsonFile, originalFile, "+12345678900", "2024-03-12T14:30:22Z"
        );

        assertEquals(wavFile, organized.getWavFile());
        assertEquals(jsonFile, organized.getJsonFile());
        assertEquals(originalFile, organized.getOriginalFile());
        assertEquals("+12345678900", organized.getCallerInfo());
        assertNotNull(organized.toString());
    }

    @Test
    void testFileError() {
        Path sourceFile = tempDir.resolve("source.wav");
        Exception exception = new RuntimeException("Test error");

        OutputResult.FileError error = new OutputResult.FileError(
            sourceFile, "Failed to copy file", exception
        );

        assertEquals(sourceFile, error.getSourceFile());
        assertEquals("Failed to copy file", error.getErrorMessage());
        assertEquals(exception, error.getException());
        assertNotNull(error.toString());
    }

    @Test
    void testGenerateWavFilename() {
        VoicemailFile.VoicemailMetadata metadata = new VoicemailFile.VoicemailMetadata(
            1, 100, Instant.parse("2024-03-12T14:30:22Z"), "+12345678900", null,
            45, null, null, 0
        );

        VoicemailFile file = new VoicemailFile.Builder()
            .fileId("abc123")
            .relativePath("voicemail/test.amr")
            .backupFilePath(tempDir.resolve("ab/abc123"))
            .metadata(metadata)
            .build();

        String filename = filenameGenerator.generateWavFilename(file);

        assertNotNull(filename);
        assertTrue(filename.endsWith(".wav"));
        assertTrue(filename.matches("\\d{8}-\\d{6}-.+\\.wav"));
    }

    @Test
    void testGenerateJsonFilename() {
        VoicemailFile.VoicemailMetadata metadata = new VoicemailFile.VoicemailMetadata(
            1, 100, Instant.parse("2024-03-12T14:30:22Z"), "+12345678900", null,
            45, null, null, 0
        );

        VoicemailFile file = new VoicemailFile.Builder()
            .fileId("abc123")
            .relativePath("voicemail/test.amr")
            .backupFilePath(tempDir.resolve("ab/abc123"))
            .metadata(metadata)
            .build();

        String filename = filenameGenerator.generateJsonFilename(file);

        assertNotNull(filename);
        assertTrue(filename.endsWith(".json"));
        assertTrue(filename.matches("\\d{8}-\\d{6}-.+\\.json"));
    }

    @Test
    void testGenerateOriginalFilename() {
        VoicemailFile.VoicemailMetadata metadata = new VoicemailFile.VoicemailMetadata(
            1, 100, Instant.parse("2024-03-12T14:30:22Z"), "+12345678900", null,
            45, null, null, 0
        );

        VoicemailFile file = new VoicemailFile.Builder()
            .fileId("abc123")
            .relativePath("voicemail/test.amr")
            .backupFilePath(tempDir.resolve("ab/abc123"))
            .metadata(metadata)
            .build();

        String filename = filenameGenerator.generateOriginalFilename(file, "amr");

        assertNotNull(filename);
        assertTrue(filename.endsWith(".amr"));
        assertTrue(filename.matches("\\d{8}-\\d{6}-.+\\.amr"));
    }

    @Test
    void testGenerateUniqueFilename_noCollision() {
        Set<String> existingNames = new HashSet<>();
        String baseFilename = "20240312-143022-John_Smith.wav";

        String unique = filenameGenerator.generateUniqueFilename(baseFilename, existingNames);

        assertEquals(baseFilename, unique);
    }

    @Test
    void testGenerateUniqueFilename_withCollision() {
        Set<String> existingNames = new HashSet<>();
        existingNames.add("20240312-143022-John_Smith.wav");
        String baseFilename = "20240312-143022-John_Smith.wav";

        String unique = filenameGenerator.generateUniqueFilename(baseFilename, existingNames);

        assertEquals("20240312-143022-John_Smith-1.wav", unique);
    }

    @Test
    void testGenerateUniqueFilename_multipleCollisions() {
        Set<String> existingNames = new HashSet<>();
        existingNames.add("20240312-143022-John_Smith.wav");
        existingNames.add("20240312-143022-John_Smith-1.wav");
        existingNames.add("20240312-143022-John_Smith-2.wav");
        String baseFilename = "20240312-143022-John_Smith.wav";

        String unique = filenameGenerator.generateUniqueFilename(baseFilename, existingNames);

        assertEquals("20240312-143022-John_Smith-3.wav", unique);
    }

    @Test
    void testCreateDateDirectory() throws Exception {
        Path baseDir = tempDir.resolve("voicemail-wavs");
        Instant timestamp = Instant.parse("2024-03-12T14:30:22Z");

        Path dateDir = directoryCreator.createDateDirectory(baseDir, timestamp);

        assertTrue(Files.exists(dateDir));
        assertTrue(Files.isDirectory(dateDir));
        String dirName = dateDir.getFileName().toString();
        assertTrue(dirName.matches("\\d{4}-\\d{2}-\\d{2}"));
    }

    @Test
    void testEnsureBaseDirectoriesExist() throws Exception {
        Path wavDir = tempDir.resolve("voicemail-wavs");
        Path backupDir = tempDir.resolve("voicemail-backup");

        directoryCreator.ensureBaseDirectoriesExist(wavDir, backupDir);

        assertTrue(Files.exists(wavDir));
        assertTrue(Files.isDirectory(wavDir));
        assertTrue(Files.isWritable(wavDir));
        assertTrue(Files.exists(backupDir));
        assertTrue(Files.isDirectory(backupDir));
        assertTrue(Files.isWritable(backupDir));
    }

    @Test
    void testEnsureBaseDirectoriesExist_nullBackupDir() throws Exception {
        Path wavDir = tempDir.resolve("voicemail-wavs");

        directoryCreator.ensureBaseDirectoriesExist(wavDir, null);

        assertTrue(Files.exists(wavDir));
        assertTrue(Files.isDirectory(wavDir));
        assertTrue(Files.isWritable(wavDir));
    }

    @Test
    void testHasSufficientSpace() {
        Path outputDir = tempDir;
        long requiredBytes = 1000;

        boolean sufficient = directoryCreator.hasSufficientSpace(outputDir, requiredBytes);

        assertTrue(sufficient);
    }

    @Test
    void testOriginalFileKeeper_isEnabled() {
        OriginalFileKeeper keeper = new OriginalFileKeeper(filenameGenerator, directoryCreator);

        assertTrue(keeper.isKeepOriginalsEnabled(tempDir));
        assertFalse(keeper.isKeepOriginalsEnabled(null));
    }

    @Test
    void testOriginalFileKeeper_copyOriginalFile() throws Exception {
        Path originalFile = tempDir.resolve("original.amr");
        Files.writeString(originalFile, "test content");

        VoicemailFile.VoicemailMetadata metadata = new VoicemailFile.VoicemailMetadata(
            1, 100, Instant.parse("2024-03-12T14:30:22Z"), "+12345678900", null,
            45, null, null, 0
        );

        VoicemailFile file = new VoicemailFile.Builder()
            .fileId("abc123")
            .relativePath("voicemail/test.amr")
            .backupFilePath(tempDir.resolve("ab/abc123"))
            .metadata(metadata)
            .build();

        Path backupDir = tempDir.resolve("backup");
        OriginalFileKeeper keeper = new OriginalFileKeeper(filenameGenerator, directoryCreator);

        Path copiedFile = keeper.copyOriginalFile(originalFile, file, backupDir);

        assertNotNull(copiedFile);
        assertTrue(Files.exists(copiedFile));
        assertEquals("test content", Files.readString(copiedFile));
    }

    @Test
    void testOriginalFileKeeper_copyOriginalFile_nullBackupDir() throws Exception {
        Path originalFile = tempDir.resolve("original.amr");
        Files.writeString(originalFile, "test content");

        VoicemailFile.VoicemailMetadata metadata = new VoicemailFile.VoicemailMetadata(
            1, 100, Instant.parse("2024-03-12T14:30:22Z"), "+12345678900", null,
            45, null, null, 0
        );

        VoicemailFile file = new VoicemailFile.Builder()
            .fileId("abc123")
            .relativePath("voicemail/test.amr")
            .backupFilePath(tempDir.resolve("ab/abc123"))
            .metadata(metadata)
            .build();

        OriginalFileKeeper keeper = new OriginalFileKeeper(filenameGenerator, directoryCreator);

        Path copiedFile = keeper.copyOriginalFile(originalFile, file, null);

        assertNull(copiedFile);
    }
}
