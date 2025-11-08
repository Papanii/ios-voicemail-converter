package com.voicemail;

import com.voicemail.backup.BackupDiscovery;
import com.voicemail.backup.BackupInfo;
import com.voicemail.cli.Arguments;
import com.voicemail.converter.AudioConverter;
import com.voicemail.converter.ConversionResult;
import com.voicemail.exception.*;
import com.voicemail.extractor.VoicemailExtractor;
import com.voicemail.extractor.VoicemailFile;
import com.voicemail.metadata.MetadataProcessor;
import com.voicemail.output.FileOrganizer;
import com.voicemail.output.OutputResult;
import com.voicemail.util.FormatUtil;
import com.voicemail.util.TempDirectoryManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Main orchestrator for the iOS Voicemail Converter application.
 * <p>
 * Coordinates the entire workflow:
 * 1. Discover iOS backup
 * 2. Extract voicemail files
 * 3. Process metadata
 * 4. Convert audio to WAV
 * 5. Organize output files
 * 6. Display summary
 * 7. Clean up temporary files
 * </p>
 */
public class VoicemailConverter {
    private static final Logger log = LoggerFactory.getLogger(VoicemailConverter.class);

    private final Arguments arguments;
    private final TempDirectoryManager tempManager;

    public VoicemailConverter(Arguments arguments) {
        this.arguments = arguments;
        this.tempManager = new TempDirectoryManager();
    }

    /**
     * Execute the complete voicemail conversion workflow.
     *
     * @return exit code (0 for success, non-zero for errors)
     */
    public int run() {
        Instant startTime = Instant.now();

        try {
            log.info("Starting iOS Voicemail Converter");
            displayConfiguration();

            // Step 1: Discover iOS backup
            BackupInfo backup = discoverBackup();
            displayBackupInfo(backup);

            // Step 2: Extract voicemail files
            List<VoicemailFile> voicemails = extractVoicemails(backup);

            if (voicemails.isEmpty()) {
                throw new NoVoicemailsException();
            }

            System.out.println("Found " + voicemails.size() + " voicemail(s) to convert\n");

            // Step 3: Process metadata
            List<MetadataProcessor.ProcessedMetadata> metadataList = processMetadata(voicemails, backup);

            // Step 4: Convert audio files
            List<ConversionResult> conversionResults = convertAudio(voicemails, metadataList);

            // Step 5: Organize output files
            OutputResult outputResult = organizeOutput(conversionResults, voicemails, metadataList);

            // Step 6: Display summary
            Duration totalTime = Duration.between(startTime, Instant.now());
            displaySummary(voicemails.size(), conversionResults, outputResult, totalTime);

            // Clean up temp files
            cleanup();

            log.info("Conversion completed successfully in {}", FormatUtil.formatDuration(totalTime));
            return 0;

        } catch (NoVoicemailsException e) {
            System.err.println("\nNo voicemails found in backup.");
            System.err.println("The backup may not contain any voicemail data.");
            log.warn("No voicemails found in backup: {}", e.getMessage());
            cleanup();
            return e.getExitCode();

        } catch (BackupException e) {
            System.err.println("\nError: " + e.getMessage());
            if (e.hasSuggestion()) {
                System.err.println("\nSuggestion:");
                System.err.println("  " + e.getSuggestion());
            }
            log.error("Backup error: {}", e.getMessage());
            cleanup();
            return e.getExitCode();

        } catch (DependencyException e) {
            System.err.println("\nError: " + e.getMessage());
            if (e.hasSuggestion()) {
                System.err.println("\nSuggestion:");
                System.err.println("  " + e.getSuggestion());
            }
            log.error("Dependency error: {}", e.getMessage());
            cleanup();
            return e.getExitCode();

        } catch (PermissionException e) {
            System.err.println("\nError: " + e.getMessage());
            if (e.hasSuggestion()) {
                System.err.println("\nSuggestion:");
                System.err.println("  " + e.getSuggestion());
            }
            log.error("Permission error: {}", e.getMessage());
            cleanup();
            return e.getExitCode();

        } catch (Exception e) {
            System.err.println("\nUnexpected error: " + e.getMessage());
            log.error("Unexpected error during conversion", e);
            cleanup();
            return 1;
        }
    }

    private void displayConfiguration() {
        System.out.println("iOS Voicemail Converter");
        System.out.println("======================\n");
        System.out.println("Configuration:");
        System.out.println("  Output Directory:    " + arguments.getOutputDir());
        if (arguments.isKeepOriginals()) {
            System.out.println("  Backup Directory:    ./voicemail-backup/");
        }
        System.out.println("  Keep Originals:      " + (arguments.isKeepOriginals() ? "Yes" : "No"));
        System.out.println("  Include Metadata:    " + (arguments.isIncludeMetadata() ? "Yes" : "No"));
        System.out.println();
    }

    private BackupInfo discoverBackup() throws BackupException {
        System.out.println("Discovering iOS backups...");

        BackupDiscovery discovery = new BackupDiscovery();
        return discovery.discoverBackup(arguments);
    }

    private void displayBackupInfo(BackupInfo backup) {
        System.out.println("Using backup:");
        System.out.println("  Device:  " + backup.getDeviceName());
        System.out.println("  iOS:     " + backup.getProductVersion());
        if (backup.getLastBackupDate() != null) {
            System.out.println("  Date:    " + FormatUtil.formatTimestampForDisplay(
                backup.getLastBackupDate().atZone(ZoneId.systemDefault()).toInstant()));
        }
        System.out.println();
    }

    private List<VoicemailFile> extractVoicemails(BackupInfo backup) throws Exception {
        System.out.println("Extracting voicemails from backup...");

        tempManager.createTempDirectory();
        VoicemailExtractor extractor = new VoicemailExtractor(backup, tempManager);
        List<VoicemailFile> voicemails = extractor.extractVoicemails();

        log.info("Extracted {} voicemails", voicemails.size());
        return voicemails;
    }

    private List<MetadataProcessor.ProcessedMetadata> processMetadata(
            List<VoicemailFile> voicemails, BackupInfo backup) {

        System.out.println("Processing metadata...");

        MetadataProcessor processor = new MetadataProcessor();
        List<MetadataProcessor.ProcessedMetadata> metadataList = new ArrayList<>();

        for (VoicemailFile voicemail : voicemails) {
            Instant backupInstant = backup.getLastBackupDate() != null ?
                backup.getLastBackupDate().atZone(ZoneId.systemDefault()).toInstant() :
                Instant.now();

            MetadataProcessor.ProcessedMetadata metadata = processor.processMetadata(
                voicemail,
                backup.getDeviceName(),
                backup.getProductVersion(),
                backupInstant
            );
            metadataList.add(metadata);

            // Export JSON if requested
            if (arguments.isIncludeMetadata()) {
                try {
                    Path jsonPath = tempManager.getTempDirectory().resolve(
                        voicemail.getFileId() + ".json");
                    processor.exportToJSON(metadata, jsonPath);
                } catch (Exception e) {
                    log.warn("Failed to export metadata for {}: {}",
                        voicemail.getOriginalFilename(), e.getMessage());
                }
            }
        }

        return metadataList;
    }

    private List<ConversionResult> convertAudio(
            List<VoicemailFile> voicemails,
            List<MetadataProcessor.ProcessedMetadata> metadataList) throws DependencyException {

        System.out.println("Converting audio files...");

        MetadataProcessor metadataProcessor = new MetadataProcessor();
        AudioConverter converter = new AudioConverter(metadataProcessor);

        List<ConversionResult> results = new ArrayList<>();
        int total = voicemails.size();

        for (int i = 0; i < total; i++) {
            VoicemailFile voicemail = voicemails.get(i);
            MetadataProcessor.ProcessedMetadata metadata = metadataList.get(i);

            System.out.printf("  [%d/%d] Converting %s...",
                i + 1, total, voicemail.getOriginalFilename());

            try {
                Path outputWav = tempManager.getTempDirectory().resolve(
                    voicemail.getFileId() + ".wav");

                ConversionResult result = converter.convertToWav(voicemail, outputWav, metadata);
                results.add(result);

                if (result.isSuccess()) {
                    System.out.println(" ✓");
                } else {
                    System.out.println(" ✗ (" + result.getErrorMessage() + ")");
                }

            } catch (Exception e) {
                System.out.println(" ✗ (" + e.getMessage() + ")");
                log.error("Conversion failed for {}", voicemail.getOriginalFilename(), e);
            }
        }

        long successful = results.stream().filter(ConversionResult::isSuccess).count();
        System.out.println("Converted: " + successful + "/" + total + " successful\n");

        return results;
    }

    private OutputResult organizeOutput(
            List<ConversionResult> conversionResults,
            List<VoicemailFile> voicemails,
            List<MetadataProcessor.ProcessedMetadata> metadataList) throws PermissionException {

        System.out.println("Organizing output files...");

        FileOrganizer organizer = new FileOrganizer();

        // Build list of files to organize (only successful conversions)
        List<FileOrganizer.FileToOrganize> filesToOrganize = new ArrayList<>();

        for (int i = 0; i < conversionResults.size(); i++) {
            ConversionResult result = conversionResults.get(i);
            if (!result.isSuccess()) {
                continue;
            }

            VoicemailFile voicemail = voicemails.get(i);
            MetadataProcessor.ProcessedMetadata metadata = metadataList.get(i);

            // Get JSON file path if metadata was exported
            Path jsonFile = null;
            if (arguments.isIncludeMetadata()) {
                jsonFile = tempManager.getTempDirectory().resolve(voicemail.getFileId() + ".json");
            }

            FileOrganizer.FileToOrganize fileToOrganize = new FileOrganizer.FileToOrganize(
                result.getOutputFile(),  // WAV file
                jsonFile,                 // JSON metadata
                voicemail.getExtractedPath(),  // Original AMR file
                voicemail
            );

            filesToOrganize.add(fileToOrganize);
        }

        // Determine backup directory
        Path backupDir = arguments.isKeepOriginals() ?
            arguments.getOutputDir().getParent().resolve("voicemail-backup") : null;

        // Organize files
        OutputResult result = organizer.organizeFiles(
            filesToOrganize,
            arguments.getOutputDir(),
            backupDir
        );

        System.out.println("Organized: " + result.getSuccessfulFiles() + " file(s)\n");

        return result;
    }

    private void displaySummary(int totalVoicemails,
                                List<ConversionResult> conversionResults,
                                OutputResult outputResult,
                                Duration totalTime) {

        System.out.println("======================");
        System.out.println("Conversion Summary");
        System.out.println("======================\n");

        long successfulConversions = conversionResults.stream()
            .filter(ConversionResult::isSuccess)
            .count();

        System.out.println("Voicemails Found:    " + totalVoicemails);
        System.out.println("Converted:           " + successfulConversions + "/" + totalVoicemails);
        System.out.println("Organized:           " + outputResult.getSuccessfulFiles());

        if (outputResult.hasErrors()) {
            System.out.println("Errors:              " + outputResult.getFailedFiles());
        }

        System.out.println("Total Time:          " + FormatUtil.formatDuration(totalTime));
        System.out.println();

        // Show output locations
        System.out.println("Output:");
        System.out.println("  WAV files:  " + arguments.getOutputDir());

        if (arguments.isKeepOriginals()) {
            System.out.println("  Originals:  " +
                arguments.getOutputDir().getParent().resolve("voicemail-backup"));
        }

        if (arguments.isIncludeMetadata()) {
            System.out.println("  Metadata:   " + arguments.getOutputDir() + " (*.json)");
        }

        System.out.println();

        // Display errors if any
        if (outputResult.hasErrors()) {
            System.out.println("Errors:");
            for (OutputResult.FileError error : outputResult.getErrors()) {
                System.out.println("  - " + error.getSourceFile().getFileName() +
                    ": " + error.getErrorMessage());
            }
            System.out.println();
        }

        System.out.println("✓ Conversion complete!");
    }

    private void cleanup() {
        try {
            tempManager.cleanup();
            log.debug("Temporary files cleaned up");
        } catch (Exception e) {
            log.warn("Failed to clean up temporary files", e);
        }
    }
}
