# Converter Module Implementation Guide

**Module:** Audio Conversion
**Package:** `com.voicemail.converter`
**Status:** Not Implemented
**Priority:** High (Core functionality)

---

## Overview

The Converter module handles audio conversion from AMR/AMR-WB/AAC to WAV format using FFmpeg. This includes FFmpeg detection, audio analysis with ffprobe, conversion execution, and progress tracking.

## Module Purpose

Handle:
1. FFmpeg/ffprobe detection and validation
2. Audio file analysis (format, duration, quality)
3. Audio conversion (AMR → WAV)
4. Progress tracking during conversion
5. Output validation

---

## Dependencies

### External Tools
- **FFmpeg 4.0+** - Audio conversion (external binary)
- **ffprobe** - Audio analysis (included with FFmpeg)

### Internal Dependencies
- `com.voicemail.extractor.VoicemailFile` - Input files
- `com.voicemail.metadata.MetadataProcessor` - Metadata embedding
- `com.voicemail.exception.*` - Exception classes
- `com.voicemail.util.FormatUtil` - Formatting utilities

---

## Implementation Order

1. **ConversionResult** - Data class for results (first)
2. **FFmpegDetector** - Detect FFmpeg installation
3. **AudioAnalyzer** - Analyze audio with ffprobe
4. **ProgressTracker** - Track conversion progress
5. **FFmpegWrapper** - Execute FFmpeg commands
6. **AudioConverter** - Main orchestrator (last)

---

## Class 1: ConversionResult

**Location:** `src/main/java/com/voicemail/converter/ConversionResult.java`

### Implementation

```java
package com.voicemail.converter;

import java.nio.file.Path;
import java.time.Duration;

/**
 * Result of an audio conversion operation
 */
public class ConversionResult {
    private final boolean success;
    private final Path inputFile;
    private final Path outputFile;
    private final Duration conversionTime;
    private final long inputSize;
    private final long outputSize;
    private final String errorMessage;
    private final AudioInfo audioInfo;

    /**
     * Audio information
     */
    public static class AudioInfo {
        private final String codec;
        private final int sampleRate;
        private final int channels;
        private final int bitRate;
        private final double durationSeconds;

        public AudioInfo(String codec, int sampleRate, int channels,
                        int bitRate, double durationSeconds) {
            this.codec = codec;
            this.sampleRate = sampleRate;
            this.channels = channels;
            this.bitRate = bitRate;
            this.durationSeconds = durationSeconds;
        }

        public String getCodec() { return codec; }
        public int getSampleRate() { return sampleRate; }
        public int getChannels() { return channels; }
        public int getBitRate() { return bitRate; }
        public double getDurationSeconds() { return durationSeconds; }

        @Override
        public String toString() {
            return String.format("%s, %d Hz, %d ch, %.1fs",
                codec, sampleRate, channels, durationSeconds);
        }
    }

    private ConversionResult(Builder builder) {
        this.success = builder.success;
        this.inputFile = builder.inputFile;
        this.outputFile = builder.outputFile;
        this.conversionTime = builder.conversionTime;
        this.inputSize = builder.inputSize;
        this.outputSize = builder.outputSize;
        this.errorMessage = builder.errorMessage;
        this.audioInfo = builder.audioInfo;
    }

    // Getters
    public boolean isSuccess() { return success; }
    public Path getInputFile() { return inputFile; }
    public Path getOutputFile() { return outputFile; }
    public Duration getConversionTime() { return conversionTime; }
    public long getInputSize() { return inputSize; }
    public long getOutputSize() { return outputSize; }
    public String getErrorMessage() { return errorMessage; }
    public AudioInfo getAudioInfo() { return audioInfo; }

    public boolean hasError() {
        return errorMessage != null && !errorMessage.isEmpty();
    }

    @Override
    public String toString() {
        if (success) {
            return String.format("Success: %s → %s (%s in %s)",
                inputFile.getFileName(),
                outputFile.getFileName(),
                audioInfo != null ? audioInfo.toString() : "unknown",
                conversionTime != null ? conversionTime.toMillis() + "ms" : "unknown"
            );
        } else {
            return String.format("Failed: %s - %s",
                inputFile.getFileName(),
                errorMessage
            );
        }
    }

    /**
     * Builder for ConversionResult
     */
    public static class Builder {
        private boolean success;
        private Path inputFile;
        private Path outputFile;
        private Duration conversionTime;
        private long inputSize;
        private long outputSize;
        private String errorMessage;
        private AudioInfo audioInfo;

        public Builder success(boolean success) {
            this.success = success;
            return this;
        }

        public Builder inputFile(Path inputFile) {
            this.inputFile = inputFile;
            return this;
        }

        public Builder outputFile(Path outputFile) {
            this.outputFile = outputFile;
            return this;
        }

        public Builder conversionTime(Duration conversionTime) {
            this.conversionTime = conversionTime;
            return this;
        }

        public Builder inputSize(long inputSize) {
            this.inputSize = inputSize;
            return this;
        }

        public Builder outputSize(long outputSize) {
            this.outputSize = outputSize;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public Builder audioInfo(AudioInfo audioInfo) {
            this.audioInfo = audioInfo;
            return this;
        }

        public ConversionResult build() {
            return new ConversionResult(this);
        }
    }
}
```

---

## Class 2: FFmpegDetector

**Location:** `src/main/java/com/voicemail/converter/FFmpegDetector.java`

### Implementation

```java
package com.voicemail.converter;

import com.voicemail.exception.DependencyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects FFmpeg and ffprobe installation
 */
public class FFmpegDetector {
    private static final Logger log = LoggerFactory.getLogger(FFmpegDetector.class);

    private static final Pattern VERSION_PATTERN = Pattern.compile("version\\s+([0-9.]+)");

    private String ffmpegPath = "ffmpeg";
    private String ffprobePath = "ffprobe";
    private String ffmpegVersion;
    private String ffprobeVersion;

    /**
     * Detect FFmpeg installation
     */
    public void detect() throws DependencyException {
        log.info("Detecting FFmpeg installation");

        // Detect ffmpeg
        if (!detectFFmpeg()) {
            throw new DependencyException("ffmpeg", "FFmpeg not found in PATH");
        }

        // Detect ffprobe
        if (!detectFFprobe()) {
            throw new DependencyException("ffprobe", "ffprobe not found in PATH");
        }

        log.info("FFmpeg detected: version {}", ffmpegVersion);
        log.info("ffprobe detected: version {}", ffprobeVersion);
    }

    /**
     * Detect ffmpeg binary
     */
    private boolean detectFFmpeg() {
        try {
            ProcessBuilder pb = new ProcessBuilder(ffmpegPath, "-version");
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream())
            );

            String line = reader.readLine();
            if (line != null) {
                Matcher matcher = VERSION_PATTERN.matcher(line);
                if (matcher.find()) {
                    ffmpegVersion = matcher.group(1);
                    log.debug("Found ffmpeg version: {}", ffmpegVersion);
                    return true;
                }
            }

            int exitCode = process.waitFor();
            return exitCode == 0;

        } catch (Exception e) {
            log.warn("ffmpeg not detected: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Detect ffprobe binary
     */
    private boolean detectFFprobe() {
        try {
            ProcessBuilder pb = new ProcessBuilder(ffprobePath, "-version");
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream())
            );

            String line = reader.readLine();
            if (line != null) {
                Matcher matcher = VERSION_PATTERN.matcher(line);
                if (matcher.find()) {
                    ffprobeVersion = matcher.group(1);
                    log.debug("Found ffprobe version: {}", ffprobeVersion);
                    return true;
                }
            }

            int exitCode = process.waitFor();
            return exitCode == 0;

        } catch (Exception e) {
            log.warn("ffprobe not detected: {}", e.getMessage());
            return false;
        }
    }

    public String getFFmpegPath() {
        return ffmpegPath;
    }

    public String getFFprobePath() {
        return ffprobePath;
    }

    public String getFFmpegVersion() {
        return ffmpegVersion;
    }

    public String getFFprobeVersion() {
        return ffprobeVersion;
    }
}
```

---

## Class 3: AudioAnalyzer

**Location:** `src/main/java/com/voicemail/converter/AudioAnalyzer.java`

### Implementation

```java
package com.voicemail.converter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Analyzes audio files using ffprobe
 */
public class AudioAnalyzer {
    private static final Logger log = LoggerFactory.getLogger(AudioAnalyzer.class);

    private final String ffprobePath;

    // Patterns for parsing ffprobe output
    private static final Pattern CODEC_PATTERN = Pattern.compile("codec_name=(.+)");
    private static final Pattern SAMPLE_RATE_PATTERN = Pattern.compile("sample_rate=(\\d+)");
    private static final Pattern CHANNELS_PATTERN = Pattern.compile("channels=(\\d+)");
    private static final Pattern BIT_RATE_PATTERN = Pattern.compile("bit_rate=(\\d+)");
    private static final Pattern DURATION_PATTERN = Pattern.compile("duration=([0-9.]+)");

    public AudioAnalyzer(String ffprobePath) {
        this.ffprobePath = ffprobePath;
    }

    /**
     * Analyze audio file
     */
    public ConversionResult.AudioInfo analyze(Path audioFile) throws Exception {
        log.debug("Analyzing audio file: {}", audioFile);

        ProcessBuilder pb = new ProcessBuilder(
            ffprobePath,
            "-v", "quiet",
            "-print_format", "default=noprint_wrappers=1",
            "-show_format",
            "-show_streams",
            audioFile.toString()
        );

        Process process = pb.start();

        BufferedReader reader = new BufferedReader(
            new InputStreamReader(process.getInputStream())
        );

        String codec = null;
        int sampleRate = 0;
        int channels = 0;
        int bitRate = 0;
        double duration = 0.0;

        String line;
        while ((line = reader.readLine()) != null) {
            Matcher m;

            if ((m = CODEC_PATTERN.matcher(line)).find()) {
                codec = m.group(1);
            } else if ((m = SAMPLE_RATE_PATTERN.matcher(line)).find()) {
                sampleRate = Integer.parseInt(m.group(1));
            } else if ((m = CHANNELS_PATTERN.matcher(line)).find()) {
                channels = Integer.parseInt(m.group(1));
            } else if ((m = BIT_RATE_PATTERN.matcher(line)).find()) {
                bitRate = Integer.parseInt(m.group(1));
            } else if ((m = DURATION_PATTERN.matcher(line)).find()) {
                duration = Double.parseDouble(m.group(1));
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("ffprobe exited with code " + exitCode);
        }

        ConversionResult.AudioInfo info = new ConversionResult.AudioInfo(
            codec, sampleRate, channels, bitRate, duration
        );

        log.debug("Audio info: {}", info);
        return info;
    }
}
```

---

## Class 4: ProgressTracker

**Location:** `src/main/java/com/voicemail/converter/ProgressTracker.java`

### Implementation

```java
package com.voicemail.converter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tracks FFmpeg conversion progress
 */
public class ProgressTracker {
    private static final Logger log = LoggerFactory.getLogger(ProgressTracker.class);

    // Pattern for FFmpeg progress: time=00:00:12.34
    private static final Pattern TIME_PATTERN = Pattern.compile("time=(\\d{2}):(\\d{2}):(\\d{2})\\.(\\d{2})");

    private final double totalDuration;
    private double currentTime;
    private int lastReportedPercent = -1;

    public ProgressTracker(double totalDurationSeconds) {
        this.totalDuration = totalDurationSeconds;
        this.currentTime = 0.0;
    }

    /**
     * Parse FFmpeg output line and update progress
     */
    public void parseProgress(String line) {
        Matcher matcher = TIME_PATTERN.matcher(line);
        if (matcher.find()) {
            int hours = Integer.parseInt(matcher.group(1));
            int minutes = Integer.parseInt(matcher.group(2));
            int seconds = Integer.parseInt(matcher.group(3));

            currentTime = hours * 3600 + minutes * 60 + seconds;

            // Log progress at 10% intervals
            int percent = getProgressPercent();
            if (percent >= lastReportedPercent + 10) {
                log.debug("Conversion progress: {}%", percent);
                lastReportedPercent = percent;
            }
        }
    }

    /**
     * Get progress percentage
     */
    public int getProgressPercent() {
        if (totalDuration <= 0) {
            return 0;
        }
        int percent = (int) ((currentTime / totalDuration) * 100);
        return Math.min(percent, 100);
    }

    /**
     * Get current time in seconds
     */
    public double getCurrentTime() {
        return currentTime;
    }

    /**
     * Check if conversion is complete
     */
    public boolean isComplete() {
        return currentTime >= totalDuration * 0.99; // 99% threshold
    }
}
```

---

## Class 5: FFmpegWrapper

**Location:** `src/main/java/com/voicemail/converter/FFmpegWrapper.java`

### Implementation

```java
package com.voicemail.converter;

import com.voicemail.exception.ConversionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Wrapper for FFmpeg command execution
 */
public class FFmpegWrapper {
    private static final Logger log = LoggerFactory.getLogger(FFmpegWrapper.class);

    private final String ffmpegPath;

    public FFmpegWrapper(String ffmpegPath) {
        this.ffmpegPath = ffmpegPath;
    }

    /**
     * Convert audio file to WAV
     */
    public ConversionResult convertToWav(
            Path inputFile,
            Path outputFile,
            Map<String, String> metadata,
            double inputDuration) throws ConversionException {

        log.info("Converting: {} → {}", inputFile.getFileName(), outputFile.getFileName());

        Instant startTime = Instant.now();

        try {
            // Build command
            List<String> command = buildConversionCommand(
                inputFile, outputFile, metadata
            );

            log.debug("FFmpeg command: {}", String.join(" ", command));

            // Execute
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Track progress
            ProgressTracker tracker = new ProgressTracker(inputDuration);
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream())
            );

            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
                tracker.parseProgress(line);
            }

            int exitCode = process.waitFor();
            Duration conversionTime = Duration.between(startTime, Instant.now());

            if (exitCode != 0) {
                log.error("FFmpeg exited with code {}", exitCode);
                log.error("FFmpeg output: {}", output);

                throw new ConversionException(
                    inputFile,
                    "FFmpeg conversion failed",
                    extractErrorFromOutput(output.toString())
                );
            }

            log.info("Conversion completed in {}ms", conversionTime.toMillis());

            return new ConversionResult.Builder()
                .success(true)
                .inputFile(inputFile)
                .outputFile(outputFile)
                .conversionTime(conversionTime)
                .build();

        } catch (IOException | InterruptedException e) {
            throw new ConversionException(
                inputFile,
                "Error executing FFmpeg",
                e
            );
        }
    }

    /**
     * Build FFmpeg conversion command
     */
    private List<String> buildConversionCommand(
            Path inputFile,
            Path outputFile,
            Map<String, String> metadata) {

        List<String> command = new ArrayList<>();

        // FFmpeg binary
        command.add(ffmpegPath);

        // Input file
        command.add("-i");
        command.add(inputFile.toString());

        // Output format settings
        command.add("-ar");
        command.add("44100");  // Sample rate: 44.1 kHz

        command.add("-ac");
        command.add("1");      // Channels: mono

        command.add("-acodec");
        command.add("pcm_s16le");  // Codec: PCM 16-bit LE

        // Add metadata
        if (metadata != null) {
            for (Map.Entry<String, String> entry : metadata.entrySet()) {
                command.add("-metadata");
                command.add(entry.getKey() + "=" + entry.getValue());
            }
        }

        // Overwrite output
        command.add("-y");

        // Logging level
        command.add("-loglevel");
        command.add("info");

        // Show progress stats
        command.add("-stats");

        // Output file
        command.add(outputFile.toString());

        return command;
    }

    /**
     * Extract error message from FFmpeg output
     */
    private String extractErrorFromOutput(String output) {
        // Look for common error patterns
        if (output.contains("Invalid data found")) {
            return "Invalid or corrupted input file";
        } else if (output.contains("No such file")) {
            return "Input file not found";
        } else if (output.contains("Permission denied")) {
            return "Permission denied accessing file";
        } else if (output.contains("Unknown decoder")) {
            return "Unsupported audio format";
        } else {
            // Return last few lines
            String[] lines = output.split("\n");
            int start = Math.max(0, lines.length - 5);
            return String.join("\n", Arrays.copyOfRange(lines, start, lines.length));
        }
    }
}
```

---

## Class 6: AudioConverter

**Location:** `src/main/java/com/voicemail/converter/AudioConverter.java`

### Implementation

```java
package com.voicemail.converter;

import com.voicemail.exception.ConversionException;
import com.voicemail.exception.DependencyException;
import com.voicemail.extractor.VoicemailFile;
import com.voicemail.metadata.MetadataProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Main orchestrator for audio conversion
 */
public class AudioConverter {
    private static final Logger log = LoggerFactory.getLogger(AudioConverter.class);

    private final FFmpegDetector detector;
    private final AudioAnalyzer analyzer;
    private final FFmpegWrapper wrapper;
    private final MetadataProcessor metadataProcessor;

    public AudioConverter(MetadataProcessor metadataProcessor) throws DependencyException {
        this.metadataProcessor = metadataProcessor;

        // Detect FFmpeg
        this.detector = new FFmpegDetector();
        detector.detect();

        // Initialize components
        this.analyzer = new AudioAnalyzer(detector.getFFprobePath());
        this.wrapper = new FFmpegWrapper(detector.getFFmpegPath());
    }

    /**
     * Convert voicemail file to WAV
     */
    public ConversionResult convertToWav(
            VoicemailFile voicemailFile,
            Path outputFile,
            MetadataProcessor.ProcessedMetadata metadata) {

        log.info("Converting voicemail: {}", voicemailFile.getOriginalFilename());

        try {
            // Analyze input file
            ConversionResult.AudioInfo audioInfo = analyzer.analyze(
                voicemailFile.getExtractedPath()
            );

            log.debug("Input audio: {}", audioInfo);

            // Get input file size
            long inputSize = Files.size(voicemailFile.getExtractedPath());

            // Convert with metadata
            ConversionResult result = wrapper.convertToWav(
                voicemailFile.getExtractedPath(),
                outputFile,
                metadata.getWavMetadata(),
                audioInfo.getDurationSeconds()
            );

            // Get output file size
            long outputSize = Files.size(outputFile);

            // Build complete result
            return new ConversionResult.Builder()
                .success(true)
                .inputFile(voicemailFile.getExtractedPath())
                .outputFile(outputFile)
                .conversionTime(result.getConversionTime())
                .inputSize(inputSize)
                .outputSize(outputSize)
                .audioInfo(audioInfo)
                .build();

        } catch (Exception e) {
            log.error("Conversion failed: {}", voicemailFile.getOriginalFilename(), e);

            return new ConversionResult.Builder()
                .success(false)
                .inputFile(voicemailFile.getExtractedPath())
                .outputFile(outputFile)
                .errorMessage(e.getMessage())
                .build();
        }
    }

    /**
     * Convert multiple voicemail files
     */
    public List<ConversionResult> convertAll(
            List<VoicemailFile> voicemails,
            List<MetadataProcessor.ProcessedMetadata> metadataList,
            OutputPathGenerator outputPathGenerator) {

        log.info("Converting {} voicemails", voicemails.size());

        List<ConversionResult> results = new ArrayList<>();

        for (int i = 0; i < voicemails.size(); i++) {
            VoicemailFile vmFile = voicemails.get(i);
            MetadataProcessor.ProcessedMetadata metadata = metadataList.get(i);

            try {
                Path outputPath = outputPathGenerator.generatePath(vmFile, metadata);
                ConversionResult result = convertToWav(vmFile, outputPath, metadata);
                results.add(result);

                if (result.isSuccess()) {
                    log.info("Converted {}/{}: {}",
                        i + 1, voicemails.size(), vmFile.getOriginalFilename());
                } else {
                    log.warn("Failed {}/{}: {}",
                        i + 1, voicemails.size(), vmFile.getOriginalFilename());
                }

            } catch (Exception e) {
                log.error("Error converting {}: {}",
                    vmFile.getOriginalFilename(), e.getMessage());
            }
        }

        // Summary
        long successful = results.stream().filter(ConversionResult::isSuccess).count();
        long failed = results.size() - successful;

        log.info("Conversion complete: {} successful, {} failed", successful, failed);

        return results;
    }

    /**
     * Interface for generating output paths
     */
    public interface OutputPathGenerator {
        Path generatePath(VoicemailFile file, MetadataProcessor.ProcessedMetadata metadata)
            throws Exception;
    }
}
```

---

## Testing Converter Module

**Test file:** `src/test/java/com/voicemail/converter/ConverterTest.java`

```java
package com.voicemail.converter;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
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
            .build();

        assertTrue(result.isSuccess());
        assertNotNull(result.getAudioInfo());
        assertEquals(45.5, result.getAudioInfo().getDurationSeconds());
    }

    @Test
    void testProgressTracker() {
        ProgressTracker tracker = new ProgressTracker(60.0); // 60 seconds

        tracker.parseProgress("time=00:00:30.00 bitrate=...");
        assertEquals(50, tracker.getProgressPercent());

        tracker.parseProgress("time=00:00:60.00 bitrate=...");
        assertEquals(100, tracker.getProgressPercent());
    }

    // Full integration tests would require FFmpeg installation
}
```

---

## Implementation Checklist

- [ ] Implement `ConversionResult` with nested AudioInfo
- [ ] Implement `FFmpegDetector`
- [ ] Implement `AudioAnalyzer`
- [ ] Implement `ProgressTracker`
- [ ] Implement `FFmpegWrapper`
- [ ] Implement `AudioConverter`
- [ ] Write unit tests
- [ ] Test with real FFmpeg
- [ ] Test various audio formats (AMR-NB, AMR-WB, AAC)
- [ ] Test progress tracking

---

## Usage Example

```java
// Initialize
MetadataProcessor metadataProcessor = new MetadataProcessor();
AudioConverter converter = new AudioConverter(metadataProcessor);

// Convert single file
VoicemailFile vmFile = ...;
MetadataProcessor.ProcessedMetadata metadata = metadataProcessor.processMetadata(...);
Path outputPath = Paths.get("/output/voicemail.wav");

ConversionResult result = converter.convertToWav(vmFile, outputPath, metadata);

if (result.isSuccess()) {
    System.out.println("Converted successfully!");
    System.out.println("Input: " + FormatUtil.formatBytes(result.getInputSize()));
    System.out.println("Output: " + FormatUtil.formatBytes(result.getOutputSize()));
} else {
    System.err.println("Conversion failed: " + result.getErrorMessage());
}
```

---

**End of Converter Module Implementation Guide**
