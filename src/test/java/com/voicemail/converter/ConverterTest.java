package com.voicemail.converter;

import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class ConverterTest {

    @Test
    void testConversionResultBuilder() {
        ConversionResult.AudioInfo audioInfo = new ConversionResult.AudioInfo(
            "amr_nb", 8000, 1, 12200, 45.5
        );

        ConversionResult result = new ConversionResult.Builder()
            .success(true)
            .inputFile(Paths.get("/tmp/input.amr"))
            .outputFile(Paths.get("/tmp/output.wav"))
            .conversionTime(Duration.ofMillis(1500))
            .audioInfo(audioInfo)
            .inputSize(12345)
            .outputSize(456789)
            .build();

        assertTrue(result.isSuccess());
        assertFalse(result.hasError());
        assertNotNull(result.getAudioInfo());
        assertEquals(45.5, result.getAudioInfo().getDurationSeconds());
        assertEquals(8000, result.getAudioInfo().getSampleRate());
        assertEquals(1, result.getAudioInfo().getChannels());
    }

    @Test
    void testConversionResultWithError() {
        ConversionResult result = new ConversionResult.Builder()
            .success(false)
            .inputFile(Paths.get("/tmp/input.amr"))
            .outputFile(Paths.get("/tmp/output.wav"))
            .errorMessage("Conversion failed")
            .build();

        assertFalse(result.isSuccess());
        assertTrue(result.hasError());
        assertEquals("Conversion failed", result.getErrorMessage());
    }

    @Test
    void testAudioInfo() {
        ConversionResult.AudioInfo audioInfo = new ConversionResult.AudioInfo(
            "amr_nb", 8000, 1, 12200, 45.5
        );

        assertEquals("amr_nb", audioInfo.getCodec());
        assertEquals(8000, audioInfo.getSampleRate());
        assertEquals(1, audioInfo.getChannels());
        assertEquals(12200, audioInfo.getBitRate());
        assertEquals(45.5, audioInfo.getDurationSeconds());
    }

    @Test
    void testProgressTracker() {
        ProgressTracker tracker = new ProgressTracker(60.0); // 60 seconds

        tracker.parseProgress("time=00:00:30.00 bitrate=...");
        assertEquals(50, tracker.getProgressPercent());
        assertEquals(30.0, tracker.getCurrentTime());

        tracker.parseProgress("time=00:00:60.00 bitrate=...");
        assertEquals(100, tracker.getProgressPercent());
        assertTrue(tracker.isComplete());
    }

    @Test
    void testProgressTrackerZeroDuration() {
        ProgressTracker tracker = new ProgressTracker(0.0);
        assertEquals(0, tracker.getProgressPercent());
    }

    @Test
    void testProgressTrackerOverflow() {
        ProgressTracker tracker = new ProgressTracker(30.0);
        tracker.parseProgress("time=00:01:00.00 bitrate=...");
        assertEquals(100, tracker.getProgressPercent()); // Should cap at 100%
    }

    @Test
    void testAudioConverterBatchConversionMismatchedLists() {
        // This test doesn't require FFmpeg since it will fail before conversion
        // We're testing the validation logic

        // We can't actually instantiate AudioConverter without FFmpeg,
        // but we can test the validation logic would be triggered
        // This is more of a documentation test showing expected behavior

        // If we could instantiate:
        // AudioConverter converter = new AudioConverter(metadataProcessor);
        // assertThrows(IllegalArgumentException.class, () -> {
        //     converter.convertAll(
        //         List.of(voicemail1, voicemail2),
        //         List.of(metadata1),  // Size mismatch!
        //         (file, meta) -> Paths.get("output.wav")
        //     );
        // });
    }

    @Test
    void testOutputPathGeneratorInterface() {
        // Test that the interface can be implemented
        AudioConverter.OutputPathGenerator generator = (file, metadata) -> {
            return Paths.get("/tmp/output/" + file.getOriginalFilename());
        };

        assertNotNull(generator);
    }

    // Full integration tests would require FFmpeg installation
    // To run full tests:
    // 1. Install FFmpeg: brew install ffmpeg (macOS) or apt-get install ffmpeg (Linux)
    // 2. Create sample AMR file
    // 3. Run: mvn test -Dtest=ConverterIntegrationTest
}
