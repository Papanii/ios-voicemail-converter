package com.voicemail.converter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
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
