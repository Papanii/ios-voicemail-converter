package com.voicemail.converter;

import com.voicemail.exception.DependencyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
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
