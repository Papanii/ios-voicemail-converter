package com.voicemail.converter;

import com.voicemail.extractor.VoicemailFile;
import com.voicemail.metadata.MetadataProcessor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for AudioConverter that require FFmpeg to be installed.
 * <p>
 * These tests will be skipped if FFmpeg is not available on the system.
 * </p>
 */
class AudioConverterIntegrationTest {

    @TempDir
    Path tempDir;

    private static Path testAmrFile;
    private static boolean ffmpegAvailable = false;

    @BeforeAll
    static void setUp() throws IOException, InterruptedException {
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
            System.out.println("WARNING: FFmpeg not found, skipping integration tests");
            System.out.println("To run these tests, install FFmpeg: brew install ffmpeg");
            return;
        }

        // Create test AMR file if FFmpeg is available
        testAmrFile = Paths.get("/tmp/voicemail-test/test-voicemail.amr");

        if (!Files.exists(testAmrFile)) {
            // Create test AMR file
            Files.createDirectories(testAmrFile.getParent());
            ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg",
                "-f", "lavfi",
                "-i", "sine=frequency=1000:duration=5",
                "-ar", "8000",
                "-ac", "1",
                "-ab", "12.2k",
                "-y",
                testAmrFile.toString()
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();
            process.waitFor();
        }
    }

    @Test
    void testAudioConverterWithRealFFmpeg() throws Exception {
        if (!ffmpegAvailable) {
            System.out.println("Skipping: FFmpeg not available");
            return;
        }

        // Create a temporary extracted file (copy of test AMR)
        Path extractedFile = tempDir.resolve("voicemail.amr");
        Files.copy(testAmrFile, extractedFile);

        // Create VoicemailFile with metadata
        VoicemailFile.VoicemailMetadata metadata = new VoicemailFile.VoicemailMetadata(
            1, 100, Instant.now(), "+12345678900", null,
            5, null, null, 0
        );

        VoicemailFile voicemailFile = new VoicemailFile.Builder()
            .fileId("test123")
            .relativePath("voicemail/1234567890.amr")
            .backupFilePath(extractedFile)
            .extractedPath(extractedFile)
            .metadata(metadata)
            .format(VoicemailFile.AudioFormat.AMR_NB)
            .fileSize(Files.size(extractedFile))
            .build();

        // Create MetadataProcessor and process metadata
        MetadataProcessor metadataProcessor = new MetadataProcessor();
        MetadataProcessor.ProcessedMetadata processedMetadata = metadataProcessor.processMetadata(
            voicemailFile,
            "Test iPhone",
            "17.0",
            Instant.now()
        );

        // Create AudioConverter
        AudioConverter converter = new AudioConverter(metadataProcessor);

        // Test version info
        assertNotNull(converter.getFFmpegVersion());
        assertNotNull(converter.getFFprobeVersion());
        System.out.println("FFmpeg version: " + converter.getFFmpegVersion());
        System.out.println("ffprobe version: " + converter.getFFprobeVersion());

        // Convert to WAV
        Path outputWav = tempDir.resolve("output.wav");
        ConversionResult result = converter.convertToWav(voicemailFile, outputWav, processedMetadata);

        // Verify result
        assertTrue(result.isSuccess(), "Conversion should succeed");
        assertFalse(result.hasError(), "Should not have errors");
        assertTrue(Files.exists(outputWav), "Output WAV file should exist");
        assertTrue(Files.size(outputWav) > 0, "Output WAV file should not be empty");

        // Verify audio info
        assertNotNull(result.getAudioInfo(), "Should have audio info");
        assertEquals("amr_nb", result.getAudioInfo().getCodec().toLowerCase());
        assertEquals(8000, result.getAudioInfo().getSampleRate());
        assertEquals(1, result.getAudioInfo().getChannels());
        assertTrue(result.getAudioInfo().getDurationSeconds() > 4.0, "Duration should be ~5 seconds");
        assertTrue(result.getAudioInfo().getDurationSeconds() < 6.0, "Duration should be ~5 seconds");

        // Verify file sizes
        assertTrue(result.getInputSize() > 0, "Input size should be set");
        assertTrue(result.getOutputSize() > 0, "Output size should be set");
        assertTrue(result.getOutputSize() > result.getInputSize(), "WAV should be larger than AMR");

        // Verify conversion time
        assertNotNull(result.getConversionTime(), "Should have conversion time");
        assertTrue(result.getConversionTime().toMillis() > 0, "Conversion time should be > 0");

        System.out.println("✅ Conversion successful:");
        System.out.println("   Input:  " + result.getInputSize() + " bytes (AMR)");
        System.out.println("   Output: " + result.getOutputSize() + " bytes (WAV)");
        System.out.println("   Duration: " + result.getAudioInfo().getDurationSeconds() + " seconds");
        System.out.println("   Time: " + result.getConversionTime().toMillis() + " ms");
    }

    @Test
    void testAudioConverterWithInvalidFile() throws Exception {
        if (!ffmpegAvailable) {
            System.out.println("Skipping: FFmpeg not available");
            return;
        }

        // Create a non-existent file
        Path nonExistentFile = tempDir.resolve("nonexistent.amr");

        VoicemailFile.VoicemailMetadata metadata = new VoicemailFile.VoicemailMetadata(
            1, 100, Instant.now(), "+12345678900", null,
            5, null, null, 0
        );

        VoicemailFile voicemailFile = new VoicemailFile.Builder()
            .fileId("test123")
            .relativePath("voicemail/nonexistent.amr")
            .backupFilePath(nonExistentFile)
            .extractedPath(nonExistentFile)
            .metadata(metadata)
            .format(VoicemailFile.AudioFormat.AMR_NB)
            .fileSize(0)
            .build();

        MetadataProcessor metadataProcessor = new MetadataProcessor();
        MetadataProcessor.ProcessedMetadata processedMetadata = metadataProcessor.processMetadata(
            voicemailFile,
            "Test iPhone",
            "17.0",
            Instant.now()
        );

        AudioConverter converter = new AudioConverter(metadataProcessor);

        // Convert should fail gracefully
        Path outputWav = tempDir.resolve("output.wav");
        ConversionResult result = converter.convertToWav(voicemailFile, outputWav, processedMetadata);

        // Verify error handling
        assertFalse(result.isSuccess(), "Conversion should fail");
        assertTrue(result.hasError(), "Should have error");
        assertNotNull(result.getErrorMessage(), "Should have error message");
        System.out.println("✅ Error handled gracefully: " + result.getErrorMessage());
    }
}
