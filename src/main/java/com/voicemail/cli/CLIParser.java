package com.voicemail.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.voicemail.exception.ConfigurationException;

/**
 * Parses and validates command-line arguments
 */
public class CLIParser {
    private static final Logger log = LoggerFactory.getLogger(CLIParser.class);

    private final HelpFormatter helpFormatter;
    private final Options options;

    // Validation patterns
    private static final Pattern UDID_HEX_PATTERN = Pattern.compile("^[0-9a-fA-F]{40}$");
    private static final Pattern UDID_UUID_PATTERN = Pattern.compile(
        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    );

    public CLIParser() {
        this.helpFormatter = new HelpFormatter();
        this.options = buildOptions();
    }

    /**
     * Build Commons CLI Options object with all argument definitions
     */
    private Options buildOptions() {
        Options opts = new Options();

        // Backup directory
        opts.addOption(Option.builder("b")
            .longOpt("backup-dir")
            .hasArg()
            .argName("path")
            .desc("iOS backup directory (default: auto-detect)")
            .build());

        // Output directory
        opts.addOption(Option.builder("o")
            .longOpt("output-dir")
            .hasArg()
            .argName("path")
            .desc("Output directory (default: ./voicemail-wavs/)")
            .build());

        // Device ID
        opts.addOption(Option.builder("d")
            .longOpt("device-id")
            .hasArg()
            .argName("udid")
            .desc("Target specific device UDID")
            .build());

        // Backup password
        opts.addOption(Option.builder("p")
            .longOpt("backup-password")
            .hasArg()
            .argName("pass")
            .desc("Password for encrypted backup")
            .build());

        // Format
        opts.addOption(Option.builder("f")
            .longOpt("format")
            .hasArg()
            .argName("format")
            .desc("Output format: wav (default: wav)")
            .build());

        // Keep originals flag
        opts.addOption(Option.builder()
            .longOpt("keep-originals")
            .desc("Copy original AMR files to ./voicemail-backup/")
            .build());

        // Include metadata flag
        opts.addOption(Option.builder()
            .longOpt("include-metadata")
            .desc("Export metadata as JSON files")
            .build());

        // Verbose flag
        opts.addOption(Option.builder("v")
            .longOpt("verbose")
            .desc("Enable detailed logging")
            .build());

        // Log file
        opts.addOption(Option.builder("l")
            .longOpt("log-file")
            .hasArg()
            .argName("path")
            .desc("Write logs to file")
            .build());

        // Help
        opts.addOption(Option.builder("h")
            .longOpt("help")
            .desc("Show this help message")
            .build());

        // Version
        opts.addOption(Option.builder()
            .longOpt("version")
            .desc("Show version information")
            .build());

        return opts;
    }

    /**
     * Parse command-line arguments
     * @param args Raw command-line arguments
     * @return Parsed and validated Arguments object
     * @throws ConfigurationException if arguments are invalid
     */
    public Arguments parse(String[] args) throws ConfigurationException {
        // Check for help or version first
        if (containsHelpOrVersion(args)) {
            return null; // Caller should check for null and exit
        }

        try {
            CommandLineParser parser = new DefaultParser();
            CommandLine cmd = parser.parse(options, args);

            // Build Arguments using Builder pattern
            Arguments.Builder builder = new Arguments.Builder();

            // Parse and validate each argument
            parseBackupDir(cmd, builder);
            parseOutputDir(cmd, builder);
            parseDeviceId(cmd, builder);
            parseBackupPassword(cmd, builder);
            parseFormat(cmd, builder);
            parseFlags(cmd, builder);
            parseLogFile(cmd, builder);

            return builder.build();

        } catch (ParseException e) {
            throw new ConfigurationException(
                "Invalid arguments: " + e.getMessage(),
                2,
                "Run with --help for usage information"
            );
        }
    }

    /**
     * Check if help or version flags are present
     * If so, display and return true
     */
    private boolean containsHelpOrVersion(String[] args) {
        for (String arg : args) {
            if ("--help".equals(arg) || "-h".equals(arg)) {
                printHelp();
                return true;
            }
            if ("--version".equals(arg)) {
                printVersion();
                return true;
            }
        }
        return false;
    }

    /**
     * Parse backup directory
     */
    private void parseBackupDir(CommandLine cmd, Arguments.Builder builder)
            throws ConfigurationException {
        if (cmd.hasOption("backup-dir")) {
            Path backupDir = Paths.get(cmd.getOptionValue("backup-dir"));
            validateExistingDirectory(backupDir, "Backup directory");
            builder.backupDir(backupDir.toAbsolutePath());
        } else {
            // Auto-detect platform default
            Path defaultBackupDir = detectDefaultBackupDir();
            builder.backupDir(defaultBackupDir);
        }
    }

    /**
     * Parse output directory
     */
    private void parseOutputDir(CommandLine cmd, Arguments.Builder builder)
            throws ConfigurationException {
        if (cmd.hasOption("output-dir")) {
            Path outputDir = Paths.get(cmd.getOptionValue("output-dir"));
            validateOutputDirectory(outputDir);
            builder.outputDir(outputDir.toAbsolutePath());
        } else {
            // Default to ./voicemail-wavs/
            Path defaultOutputDir = Paths.get("./voicemail-wavs/").toAbsolutePath();
            builder.outputDir(defaultOutputDir);
        }
    }

    /**
     * Parse device ID
     */
    private void parseDeviceId(CommandLine cmd, Arguments.Builder builder)
            throws ConfigurationException {
        if (cmd.hasOption("device-id")) {
            String deviceId = cmd.getOptionValue("device-id");
            validateUdid(deviceId);
            builder.deviceId(deviceId);
        }
    }

    /**
     * Parse backup password
     */
    private void parseBackupPassword(CommandLine cmd, Arguments.Builder builder) {
        if (cmd.hasOption("backup-password")) {
            String password = cmd.getOptionValue("backup-password");
            builder.backupPassword(password);

            // Security warning
            log.warn("Backup password provided via command-line is visible in process list");
        }
    }

    /**
     * Parse format
     */
    private void parseFormat(CommandLine cmd, Arguments.Builder builder)
            throws ConfigurationException {
        if (cmd.hasOption("format")) {
            String format = cmd.getOptionValue("format").toLowerCase();
            validateFormat(format);
            builder.format(format);
        } else {
            builder.format("wav"); // default
        }
    }

    /**
     * Parse boolean flags
     */
    private void parseFlags(CommandLine cmd, Arguments.Builder builder) {
        builder.keepOriginals(cmd.hasOption("keep-originals"));
        builder.includeMetadata(cmd.hasOption("include-metadata"));
        builder.verbose(cmd.hasOption("verbose"));
    }

    /**
     * Parse log file
     */
    private void parseLogFile(CommandLine cmd, Arguments.Builder builder)
            throws ConfigurationException {
        if (cmd.hasOption("log-file")) {
            Path logFile = Paths.get(cmd.getOptionValue("log-file"));
            validateLogFile(logFile);
            builder.logFile(logFile.toAbsolutePath());
        }
    }

    /**
     * Validate that directory exists and is readable
     */
    private void validateExistingDirectory(Path dir, String name)
            throws ConfigurationException {
        if (!Files.exists(dir)) {
            throw new ConfigurationException(
                name + " does not exist: " + dir,
                2,
                "Check the path is correct or use auto-detection by omitting this option"
            );
        }
        if (!Files.isDirectory(dir)) {
            throw new ConfigurationException(
                name + " is not a directory: " + dir,
                2,
                "Provide a valid directory path"
            );
        }
        if (!Files.isReadable(dir)) {
            throw new ConfigurationException(
                name + " is not readable: " + dir,
                7,
                "Check file permissions"
            );
        }
    }

    /**
     * Validate output directory (may not exist yet)
     */
    private void validateOutputDirectory(Path dir) throws ConfigurationException {
        if (Files.exists(dir) && !Files.isDirectory(dir)) {
            throw new ConfigurationException(
                "Output path exists but is not a directory: " + dir,
                2,
                "Choose a different path or remove the existing file"
            );
        }

        // Check parent directory is writable
        Path parent = dir.getParent();
        if (parent != null && Files.exists(parent) && !Files.isWritable(parent)) {
            throw new ConfigurationException(
                "Cannot create output directory (parent not writable): " + dir,
                7,
                "Check file permissions on parent directory"
            );
        }
    }

    /**
     * Validate UDID format
     */
    private void validateUdid(String udid) throws ConfigurationException {
        boolean isValid = UDID_HEX_PATTERN.matcher(udid).matches() ||
                         UDID_UUID_PATTERN.matcher(udid).matches();

        if (!isValid) {
            throw new ConfigurationException(
                "Invalid device ID format: " + udid,
                2,
                "Expected 40 hexadecimal characters or UUID format (8-4-4-4-12)"
            );
        }
    }

    /**
     * Validate format
     */
    private void validateFormat(String format) throws ConfigurationException {
        if (!"wav".equalsIgnoreCase(format)) {
            throw new ConfigurationException(
                "Format '" + format + "' is not yet supported",
                2,
                "Currently supported formats: wav. MP3 and FLAC coming in future version."
            );
        }
    }

    /**
     * Validate log file path
     */
    private void validateLogFile(Path logFile) throws ConfigurationException {
        if (Files.exists(logFile) && Files.isDirectory(logFile)) {
            throw new ConfigurationException(
                "Log file path is a directory: " + logFile,
                2,
                "Specify a file path, not a directory"
            );
        }

        Path parent = logFile.getParent();
        if (parent != null && Files.exists(parent) && !Files.isWritable(parent)) {
            throw new ConfigurationException(
                "Cannot create log file (parent not writable): " + logFile,
                7,
                "Check file permissions on parent directory"
            );
        }
    }

    /**
     * Detect platform-specific default backup directory
     */
    private Path detectDefaultBackupDir() {
        String os = System.getProperty("os.name").toLowerCase();
        String home = System.getProperty("user.home");

        if (os.contains("mac")) {
            return Paths.get(home, "Library", "Application Support", "MobileSync", "Backup");
        } else if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            if (appData == null) {
                appData = home + "/AppData/Roaming";
            }
            return Paths.get(appData, "Apple Computer", "MobileSync", "Backup");
        } else {
            // Linux
            return Paths.get(home, ".local", "share", "MobileSync", "Backup");
        }
    }

    /**
     * Print help message
     */
    public void printHelp() {
        System.out.println(helpFormatter.formatHelp());
    }

    /**
     * Print version information
     */
    public void printVersion() {
        System.out.println(helpFormatter.formatVersion());
    }
}
