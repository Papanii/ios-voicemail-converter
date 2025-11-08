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
