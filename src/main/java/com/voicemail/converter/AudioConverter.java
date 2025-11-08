package com.voicemail.converter;

import com.voicemail.exception.DependencyException;
import com.voicemail.extractor.VoicemailFile;
import com.voicemail.metadata.MetadataProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Main orchestrator for audio conversion.
 * <p>
 * Coordinates FFmpeg operations including detection, analysis, and conversion
 * of voicemail files to WAV format with embedded metadata.
 * </p>
 */
public class AudioConverter {
    private static final Logger log = LoggerFactory.getLogger(AudioConverter.class);

    private final FFmpegDetector detector;
    private final AudioAnalyzer analyzer;
    private final FFmpegWrapper wrapper;
    private final MetadataProcessor metadataProcessor;

    /**
     * Create AudioConverter with MetadataProcessor for embedding metadata.
     *
     * @param metadataProcessor processor for voicemail metadata
     * @throws DependencyException if FFmpeg is not found or not usable
     */
    public AudioConverter(MetadataProcessor metadataProcessor) throws DependencyException {
        this.metadataProcessor = metadataProcessor;

        // Detect FFmpeg
        this.detector = new FFmpegDetector();
        detector.detect();

        // Initialize components
        this.analyzer = new AudioAnalyzer(detector.getFFprobePath());
        this.wrapper = new FFmpegWrapper(detector.getFFmpegPath());

        log.info("AudioConverter initialized with FFmpeg: {}", detector.getFFmpegVersion());
    }

    /**
     * Convert voicemail file to WAV format.
     *
     * @param voicemailFile input voicemail file
     * @param outputFile output WAV file path
     * @param metadata processed metadata to embed
     * @return conversion result with success status and details
     */
    public ConversionResult convertToWav(
            VoicemailFile voicemailFile,
            Path outputFile,
            MetadataProcessor.ProcessedMetadata metadata) {

        log.info("Converting voicemail: {}", voicemailFile.getOriginalFilename());

        try {
            // Ensure input file exists
            if (!Files.exists(voicemailFile.getExtractedPath())) {
                String errorMsg = "Input file not found: " + voicemailFile.getExtractedPath();
                log.error(errorMsg);
                return new ConversionResult.Builder()
                    .success(false)
                    .inputFile(voicemailFile.getExtractedPath())
                    .outputFile(outputFile)
                    .errorMessage(errorMsg)
                    .build();
            }

            // Analyze input file
            ConversionResult.AudioInfo audioInfo = analyzer.analyze(
                voicemailFile.getExtractedPath()
            );

            log.debug("Input audio: codec={}, sampleRate={}, duration={}s",
                audioInfo.getCodec(), audioInfo.getSampleRate(), audioInfo.getDurationSeconds());

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
     * Convert multiple voicemail files with progress reporting.
     *
     * @param voicemails list of voicemail files to convert
     * @param metadataList corresponding metadata for each voicemail
     * @param outputPathGenerator function to generate output paths
     * @return list of conversion results
     */
    public List<ConversionResult> convertAll(
            List<VoicemailFile> voicemails,
            List<MetadataProcessor.ProcessedMetadata> metadataList,
            OutputPathGenerator outputPathGenerator) {

        if (voicemails.size() != metadataList.size()) {
            throw new IllegalArgumentException(
                "Voicemails and metadata lists must have same size");
        }

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
                    log.warn("Failed {}/{}: {} - {}",
                        i + 1, voicemails.size(), vmFile.getOriginalFilename(),
                        result.getErrorMessage());
                }

            } catch (Exception e) {
                log.error("Error converting {}: {}",
                    vmFile.getOriginalFilename(), e.getMessage());

                // Add failed result
                ConversionResult failedResult = new ConversionResult.Builder()
                    .success(false)
                    .inputFile(vmFile.getExtractedPath())
                    .outputFile(null)
                    .errorMessage(e.getMessage())
                    .build();
                results.add(failedResult);
            }
        }

        // Summary
        long successful = results.stream().filter(ConversionResult::isSuccess).count();
        long failed = results.size() - successful;

        log.info("Conversion complete: {} successful, {} failed", successful, failed);

        return results;
    }

    /**
     * Get FFmpeg version information.
     *
     * @return FFmpeg version string
     */
    public String getFFmpegVersion() {
        return detector.getFFmpegVersion();
    }

    /**
     * Get ffprobe version information.
     *
     * @return ffprobe version string
     */
    public String getFFprobeVersion() {
        return detector.getFFprobeVersion();
    }

    /**
     * Interface for generating output paths during batch conversion.
     * <p>
     * Implementations should generate appropriate output file paths based on
     * voicemail metadata and organizational requirements.
     * </p>
     */
    public interface OutputPathGenerator {
        /**
         * Generate output path for a voicemail file.
         *
         * @param file voicemail file
         * @param metadata processed metadata
         * @return path where converted WAV file should be written
         * @throws Exception if path generation fails
         */
        Path generatePath(VoicemailFile file, MetadataProcessor.ProcessedMetadata metadata)
            throws Exception;
    }
}
