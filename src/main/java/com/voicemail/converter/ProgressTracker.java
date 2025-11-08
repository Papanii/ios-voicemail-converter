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
