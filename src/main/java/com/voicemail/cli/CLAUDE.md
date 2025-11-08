# CLI Module Implementation Guide

**Module:** Command-Line Interface (CLI)
**Package:** `com.voicemail.cli`
**Status:** Not Implemented
**Priority:** High (Entry point for application)

---

## Overview

This guide provides step-by-step instructions for implementing the CLI module. The CLI is the first module to implement as it's the entry point for the entire application.

## Module Purpose

The CLI module is responsible for:
1. Parsing command-line arguments
2. Validating user input
3. Building configuration objects
4. Providing help and version information
5. Handling CLI-specific errors

## Dependencies

### External Libraries
- Apache Commons CLI 1.6.0 (already in pom.xml)
- SLF4J 2.0.9 (already in pom.xml)

### Internal Dependencies
- `com.voicemail.exception.ConfigurationException` (to be created)

### JDK APIs
- `java.nio.file.*` - Path handling
- `java.util.*` - Collections, Optional
- `java.util.regex.Pattern` - Validation

---

## Implementation Order

Implement classes in this order to minimize dependencies:

1. **ConfigurationException** (exception package) - First
2. **Arguments** (with Builder) - Second
3. **HelpFormatter** - Third
4. **CLIParser** - Fourth (uses all above)

---

## Class 1: ConfigurationException

**Location:** `src/main/java/com/voicemail/exception/ConfigurationException.java`
**Purpose:** Custom exception for CLI argument parsing errors

### Implementation

```java
package com.voicemail.exception;

/**
 * Exception thrown when command-line arguments are invalid or incomplete.
 * This is a checked exception to force proper error handling.
 */
public class ConfigurationException extends Exception {
    private final int exitCode;
    private final String suggestion;

    /**
     * Create exception with message
     * @param message Error message
     */
    public ConfigurationException(String message) {
        this(message, 2, null);
    }

    /**
     * Create exception with message and exit code
     * @param message Error message
     * @param exitCode Exit code for application
     */
    public ConfigurationException(String message, int exitCode) {
        this(message, exitCode, null);
    }

    /**
     * Create exception with message, exit code, and suggestion
     * @param message Error message
     * @param exitCode Exit code for application
     * @param suggestion Suggested fix for user
     */
    public ConfigurationException(String message, int exitCode, String suggestion) {
        super(message);
        this.exitCode = exitCode;
        this.suggestion = suggestion;
    }

    public int getExitCode() {
        return exitCode;
    }

    public String getSuggestion() {
        return suggestion;
    }

    public boolean hasSuggestion() {
        return suggestion != null && !suggestion.isEmpty();
    }
}
```

### Testing ConfigurationException

```java
// Test file: src/test/java/com/voicemail/exception/ConfigurationExceptionTest.java

@Test
void testBasicConstructor() {
    ConfigurationException ex = new ConfigurationException("Test error");
    assertEquals("Test error", ex.getMessage());
    assertEquals(2, ex.getExitCode()); // Default
    assertFalse(ex.hasSuggestion());
}

@Test
void testWithExitCode() {
    ConfigurationException ex = new ConfigurationException("Test error", 5);
    assertEquals(5, ex.getExitCode());
}

@Test
void testWithSuggestion() {
    ConfigurationException ex = new ConfigurationException(
        "Test error", 2, "Try this fix"
    );
    assertTrue(ex.hasSuggestion());
    assertEquals("Try this fix", ex.getSuggestion());
}
```

---

## Class 2: Arguments

**Location:** `src/main/java/com/voicemail/cli/Arguments.java`
**Purpose:** Immutable configuration object containing parsed arguments

### Implementation Structure

```java
package com.voicemail.cli;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Immutable configuration object containing all parsed command-line arguments.
 * Use the Builder pattern to construct instances.
 */
public class Arguments {
    // Core paths
    private final Path backupDir;
    private final Path outputDir;

    // Backup selection
    private final String deviceId;      // nullable
    private final String backupPassword; // nullable

    // Output options
    private final String format;
    private final boolean keepOriginals;
    private final boolean includeMetadata;

    // Logging
    private final boolean verbose;
    private final Path logFile;         // nullable

    // Private constructor - force use of Builder
    private Arguments(Builder builder) {
        this.backupDir = builder.backupDir;
        this.outputDir = builder.outputDir;
        this.deviceId = builder.deviceId;
        this.backupPassword = builder.backupPassword;
        this.format = builder.format;
        this.keepOriginals = builder.keepOriginals;
        this.includeMetadata = builder.includeMetadata;
        this.verbose = builder.verbose;
        this.logFile = builder.logFile;
    }

    // Getters
    public Path getBackupDir() {
        return backupDir;
    }

    public Path getOutputDir() {
        return outputDir;
    }

    public Optional<String> getDeviceId() {
        return Optional.ofNullable(deviceId);
    }

    public Optional<String> getBackupPassword() {
        return Optional.ofNullable(backupPassword);
    }

    public String getFormat() {
        return format;
    }

    public boolean isKeepOriginals() {
        return keepOriginals;
    }

    public boolean isIncludeMetadata() {
        return includeMetadata;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public Optional<Path> getLogFile() {
        return Optional.ofNullable(logFile);
    }

    /**
     * Builder for Arguments
     */
    public static class Builder {
        private Path backupDir;
        private Path outputDir;
        private String deviceId;
        private String backupPassword;
        private String format = "wav";  // default
        private boolean keepOriginals = false;
        private boolean includeMetadata = false;
        private boolean verbose = false;
        private Path logFile;

        public Builder backupDir(Path backupDir) {
            this.backupDir = backupDir;
            return this;
        }

        public Builder outputDir(Path outputDir) {
            this.outputDir = outputDir;
            return this;
        }

        public Builder deviceId(String deviceId) {
            this.deviceId = deviceId;
            return this;
        }

        public Builder backupPassword(String backupPassword) {
            this.backupPassword = backupPassword;
            return this;
        }

        public Builder format(String format) {
            this.format = format;
            return this;
        }

        public Builder keepOriginals(boolean keepOriginals) {
            this.keepOriginals = keepOriginals;
            return this;
        }

        public Builder includeMetadata(boolean includeMetadata) {
            this.includeMetadata = includeMetadata;
            return this;
        }

        public Builder verbose(boolean verbose) {
            this.verbose = verbose;
            return this;
        }

        public Builder logFile(Path logFile) {
            this.logFile = logFile;
            return this;
        }

        /**
         * Build the Arguments object
         * @return Immutable Arguments instance
         * @throws IllegalStateException if required fields are missing
         */
        public Arguments build() {
            // Validate required fields
            if (backupDir == null) {
                throw new IllegalStateException("backupDir is required");
            }
            if (outputDir == null) {
                throw new IllegalStateException("outputDir is required");
            }

            return new Arguments(this);
        }
    }

    @Override
    public String toString() {
        return "Arguments{" +
                "backupDir=" + backupDir +
                ", outputDir=" + outputDir +
                ", deviceId=" + (deviceId != null ? "***" : "null") +
                ", backupPassword=" + (backupPassword != null ? "***" : "null") +
                ", format='" + format + '\'' +
                ", keepOriginals=" + keepOriginals +
                ", includeMetadata=" + includeMetadata +
                ", verbose=" + verbose +
                ", logFile=" + logFile +
                '}';
    }
}
```

### Testing Arguments

```java
// Test file: src/test/java/com/voicemail/cli/ArgumentsTest.java

@Test
void testBuilderWithRequiredFields() {
    Arguments args = new Arguments.Builder()
        .backupDir(Paths.get("/backup"))
        .outputDir(Paths.get("/output"))
        .build();

    assertEquals(Paths.get("/backup"), args.getBackupDir());
    assertEquals(Paths.get("/output"), args.getOutputDir());
    assertEquals("wav", args.getFormat()); // default
    assertFalse(args.isVerbose()); // default
}

@Test
void testBuilderWithAllFields() {
    Arguments args = new Arguments.Builder()
        .backupDir(Paths.get("/backup"))
        .outputDir(Paths.get("/output"))
        .deviceId("12345")
        .backupPassword("secret")
        .format("wav")
        .keepOriginals(true)
        .includeMetadata(true)
        .verbose(true)
        .logFile(Paths.get("/log.txt"))
        .build();

    assertTrue(args.getDeviceId().isPresent());
    assertEquals("12345", args.getDeviceId().get());
    assertTrue(args.isKeepOriginals());
    assertTrue(args.isVerbose());
}

@Test
void testBuilderMissingBackupDir() {
    Arguments.Builder builder = new Arguments.Builder()
        .outputDir(Paths.get("/output"));

    assertThrows(IllegalStateException.class, () -> builder.build());
}

@Test
void testOptionalFields() {
    Arguments args = new Arguments.Builder()
        .backupDir(Paths.get("/backup"))
        .outputDir(Paths.get("/output"))
        .build();

    assertFalse(args.getDeviceId().isPresent());
    assertFalse(args.getBackupPassword().isPresent());
    assertFalse(args.getLogFile().isPresent());
}
```

---

## Class 3: HelpFormatter

**Location:** `src/main/java/com/voicemail/cli/HelpFormatter.java`
**Purpose:** Format help and version text

### Implementation

```java
package com.voicemail.cli;

/**
 * Formats help and version information for display to user
 */
public class HelpFormatter {
    private static final String VERSION = "1.0.0";
    private static final String APP_NAME = "iOS Voicemail Converter";

    /**
     * Get the full help text
     * @return Formatted help text
     */
    public String formatHelp() {
        StringBuilder sb = new StringBuilder();

        sb.append(APP_NAME).append(" - Extract and convert iOS voicemails from backups\n\n");
        sb.append(formatUsage()).append("\n\n");
        sb.append(formatOptions()).append("\n\n");
        sb.append(formatExamples()).append("\n\n");
        sb.append(formatRequirements()).append("\n");

        return sb.toString();
    }

    /**
     * Get the usage line
     * @return Formatted usage text
     */
    public String formatUsage() {
        return "USAGE:\n    java -jar voicemail-converter.jar [OPTIONS]";
    }

    /**
     * Get the options section
     * @return Formatted options text
     */
    public String formatOptions() {
        StringBuilder sb = new StringBuilder("OPTIONS:");

        addOption(sb, "-b, --backup-dir <path>", "iOS backup directory (default: auto-detect)");
        addOption(sb, "-o, --output-dir <path>", "Output directory (default: ./voicemail-wavs/)");
        addOption(sb, "-d, --device-id <udid>", "Target specific device UDID");
        addOption(sb, "-p, --backup-password <pass>", "Password for encrypted backup");
        addOption(sb, "-f, --format <format>", "Output format: wav (default: wav)");
        addOption(sb, "    --keep-originals", "Copy original AMR files to ./voicemail-backup/");
        addOption(sb, "    --include-metadata", "Export metadata as JSON files");
        addOption(sb, "-v, --verbose", "Enable detailed logging");
        addOption(sb, "-l, --log-file <path>", "Write logs to file");
        addOption(sb, "-h, --help", "Show this help message");
        addOption(sb, "    --version", "Show version information");

        return sb.toString();
    }

    /**
     * Get the examples section
     * @return Formatted examples text
     */
    public String formatExamples() {
        StringBuilder sb = new StringBuilder("EXAMPLES:\n");

        sb.append("    # Basic usage (auto-detect backup, output to ./voicemail-wavs/)\n");
        sb.append("    java -jar voicemail-converter.jar\n\n");

        sb.append("    # With verbose output and original files\n");
        sb.append("    java -jar voicemail-converter.jar --verbose --keep-originals\n\n");

        sb.append("    # Custom directories\n");
        sb.append("    java -jar voicemail-converter.jar -b ~/Backups -o ~/Desktop/VM\n\n");

        sb.append("    # Encrypted backup with metadata export\n");
        sb.append("    java -jar voicemail-converter.jar -p mypassword --include-metadata\n\n");

        sb.append("    # Specific device\n");
        sb.append("    java -jar voicemail-converter.jar -d 00008030001E4D8A3602802E\n");

        return sb.toString();
    }

    /**
     * Get the requirements section
     * @return Formatted requirements text
     */
    public String formatRequirements() {
        StringBuilder sb = new StringBuilder("REQUIREMENTS:\n");
        sb.append("    - Java 17 or higher\n");
        sb.append("    - FFmpeg installed and in PATH\n");
        sb.append("    - Existing iOS backup (created via iTunes/Finder)\n\n");
        sb.append("For more information: https://github.com/yourusername/voicemail-converter");
        return sb.toString();
    }

    /**
     * Get version information
     * @return Formatted version text
     */
    public String formatVersion() {
        StringBuilder sb = new StringBuilder();

        sb.append(APP_NAME).append(" v").append(VERSION).append("\n\n");

        sb.append("Java Version:  ").append(System.getProperty("java.version")).append("\n");
        sb.append("Java VM:       ").append(System.getProperty("java.vm.name")).append("\n");
        sb.append("OS:            ").append(System.getProperty("os.name"))
          .append(" ").append(System.getProperty("os.version")).append("\n");
        sb.append("Architecture:  ").append(System.getProperty("os.arch")).append("\n\n");

        sb.append("Copyright (c) 2025 ").append(APP_NAME).append(" Contributors\n");
        sb.append("Licensed under MIT License\n\n");
        sb.append("For help: java -jar voicemail-converter.jar --help\n");

        return sb.toString();
    }

    /**
     * Helper to add an option line
     */
    private void addOption(StringBuilder sb, String option, String description) {
        sb.append("\n    ");
        sb.append(String.format("%-35s", option));
        sb.append(description);
    }
}
```

### Testing HelpFormatter

```java
// Test file: src/test/java/com/voicemail/cli/HelpFormatterTest.java

@Test
void testFormatHelp() {
    HelpFormatter formatter = new HelpFormatter();
    String help = formatter.formatHelp();

    assertNotNull(help);
    assertTrue(help.contains("iOS Voicemail Converter"));
    assertTrue(help.contains("USAGE:"));
    assertTrue(help.contains("OPTIONS:"));
    assertTrue(help.contains("EXAMPLES:"));
}

@Test
void testFormatVersion() {
    HelpFormatter formatter = new HelpFormatter();
    String version = formatter.formatVersion();

    assertNotNull(version);
    assertTrue(version.contains("v1.0.0"));
    assertTrue(version.contains("Java Version:"));
}

@Test
void testFormatUsage() {
    HelpFormatter formatter = new HelpFormatter();
    String usage = formatter.formatUsage();

    assertTrue(usage.contains("java -jar voicemail-converter.jar"));
}
```

---

## Class 4: CLIParser

**Location:** `src/main/java/com/voicemail/cli/CLIParser.java`
**Purpose:** Parse and validate command-line arguments

### Implementation (Part 1: Structure and Fields)

```java
package com.voicemail.cli;

import com.voicemail.exception.ConfigurationException;
import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

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

    // Methods continue in Part 2...
}
```

### Implementation (Part 2: Building Options)

```java
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
```

### Implementation (Part 3: Main Parse Method)

```java
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
```

### Implementation (Part 4: Parsing Individual Arguments)

```java
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
```

### Implementation (Part 5: Validation Methods)

```java
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
            return Paths.get(home, "Library/Application Support/MobileSync/Backup");
        } else if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            if (appData == null) {
                appData = home + "/AppData/Roaming";
            }
            return Paths.get(appData, "Apple Computer/MobileSync/Backup");
        } else {
            // Linux
            return Paths.get(home, ".local/share/MobileSync/Backup");
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
```

### Testing CLIParser (Critical Test Cases)

```java
// Test file: src/test/java/com/voicemail/cli/CLIParserTest.java

class CLIParserTest {
    private CLIParser parser;

    @BeforeEach
    void setUp() {
        parser = new CLIParser();
    }

    @Test
    void testParseMinimalArguments() throws Exception {
        // Create temp directory for testing
        Path tempBackup = Files.createTempDirectory("backup");

        String[] args = {"--backup-dir", tempBackup.toString()};
        Arguments result = parser.parse(args);

        assertNotNull(result);
        assertEquals(tempBackup.toAbsolutePath(), result.getBackupDir());
        assertTrue(result.getOutputDir().toString().contains("voicemail-wavs"));

        // Cleanup
        Files.delete(tempBackup);
    }

    @Test
    void testParseAllArguments() throws Exception {
        Path tempBackup = Files.createTempDirectory("backup");

        String[] args = {
            "--backup-dir", tempBackup.toString(),
            "--output-dir", "/tmp/output",
            "--device-id", "1234567890123456789012345678901234567890",
            "--backup-password", "secret",
            "--format", "wav",
            "--keep-originals",
            "--include-metadata",
            "--verbose"
        };

        Arguments result = parser.parse(args);

        assertNotNull(result);
        assertTrue(result.getDeviceId().isPresent());
        assertTrue(result.getBackupPassword().isPresent());
        assertTrue(result.isKeepOriginals());
        assertTrue(result.isIncludeMetadata());
        assertTrue(result.isVerbose());

        Files.delete(tempBackup);
    }

    @Test
    void testInvalidBackupDirectory() {
        String[] args = {"--backup-dir", "/path/that/does/not/exist"};

        assertThrows(ConfigurationException.class, () -> parser.parse(args));
    }

    @Test
    void testInvalidUdid() throws Exception {
        Path tempBackup = Files.createTempDirectory("backup");

        String[] args = {
            "--backup-dir", tempBackup.toString(),
            "--device-id", "invalid-udid"
        };

        assertThrows(ConfigurationException.class, () -> parser.parse(args));

        Files.delete(tempBackup);
    }

    @Test
    void testValidUdidHexFormat() throws Exception {
        Path tempBackup = Files.createTempDirectory("backup");

        String validHex = "1234567890abcdef1234567890abcdef12345678";
        String[] args = {
            "--backup-dir", tempBackup.toString(),
            "--device-id", validHex
        };

        Arguments result = parser.parse(args);
        assertTrue(result.getDeviceId().isPresent());
        assertEquals(validHex, result.getDeviceId().get());

        Files.delete(tempBackup);
    }

    @Test
    void testValidUdidUuidFormat() throws Exception {
        Path tempBackup = Files.createTempDirectory("backup");

        String validUuid = "12345678-1234-1234-1234-123456789012";
        String[] args = {
            "--backup-dir", tempBackup.toString(),
            "--device-id", validUuid
        };

        Arguments result = parser.parse(args);
        assertTrue(result.getDeviceId().isPresent());

        Files.delete(tempBackup);
    }

    @Test
    void testUnsupportedFormat() throws Exception {
        Path tempBackup = Files.createTempDirectory("backup");

        String[] args = {
            "--backup-dir", tempBackup.toString(),
            "--format", "mp3"
        };

        ConfigurationException ex = assertThrows(
            ConfigurationException.class,
            () -> parser.parse(args)
        );
        assertTrue(ex.getMessage().contains("not yet supported"));

        Files.delete(tempBackup);
    }

    @Test
    void testHelpFlag() {
        String[] args = {"--help"};
        Arguments result = parser.parse(args);
        assertNull(result); // Help flag returns null
    }

    @Test
    void testVersionFlag() {
        String[] args = {"--version"};
        Arguments result = parser.parse(args);
        assertNull(result); // Version flag returns null
    }

    @Test
    void testShortOptions() throws Exception {
        Path tempBackup = Files.createTempDirectory("backup");

        String[] args = {
            "-b", tempBackup.toString(),
            "-o", "/tmp/output",
            "-v"
        };

        Arguments result = parser.parse(args);
        assertNotNull(result);
        assertTrue(result.isVerbose());

        Files.delete(tempBackup);
    }
}
```

---

## Integration with Main Class

The CLI module will be called from `Main.java` like this:

```java
package com.voicemail;

import com.voicemail.cli.Arguments;
import com.voicemail.cli.CLIParser;
import com.voicemail.exception.ConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        try {
            // Parse CLI arguments
            CLIParser parser = new CLIParser();
            Arguments arguments = parser.parse(args);

            // If null, help or version was displayed, exit cleanly
            if (arguments == null) {
                System.exit(0);
            }

            log.info("Starting iOS Voicemail Converter");
            log.debug("Arguments: {}", arguments);

            // TODO: Continue with backup discovery and conversion
            // VoicemailConverter converter = new VoicemailConverter();
            // converter.run(arguments);

            System.exit(0);

        } catch (ConfigurationException e) {
            // CLI error - display to user
            System.err.println("Error: " + e.getMessage());
            if (e.hasSuggestion()) {
                System.err.println("\nSuggestion:");
                System.err.println("  " + e.getSuggestion());
            }
            System.exit(e.getExitCode());

        } catch (Exception e) {
            // Unexpected error
            log.error("Unexpected error", e);
            System.err.println("Unexpected error: " + e.getMessage());
            System.err.println("Please report this issue with log file");
            System.exit(1);
        }
    }
}
```

---

## Implementation Checklist

### Phase 1: Setup
- [ ] Create `exception` package
- [ ] Implement `ConfigurationException`
- [ ] Write tests for `ConfigurationException`

### Phase 2: Arguments
- [ ] Create `Arguments` class with all fields
- [ ] Implement Builder pattern
- [ ] Add validation in Builder.build()
- [ ] Override toString() (hide sensitive data)
- [ ] Write comprehensive tests

### Phase 3: HelpFormatter
- [ ] Implement `formatHelp()`
- [ ] Implement `formatUsage()`
- [ ] Implement `formatOptions()`
- [ ] Implement `formatExamples()`
- [ ] Implement `formatRequirements()`
- [ ] Implement `formatVersion()`
- [ ] Write tests

### Phase 4: CLIParser
- [ ] Create class structure
- [ ] Implement `buildOptions()`
- [ ] Implement `parse()` method
- [ ] Implement all `parseXxx()` methods
- [ ] Implement all `validateXxx()` methods
- [ ] Implement `detectDefaultBackupDir()`
- [ ] Implement `printHelp()` and `printVersion()`
- [ ] Write comprehensive tests

### Phase 5: Integration
- [ ] Update `Main.java` to use CLIParser
- [ ] Test end-to-end with various arguments
- [ ] Test error handling
- [ ] Test help and version display

---

## Common Pitfalls to Avoid

1. **Path Handling**
   - Always convert to absolute paths
   - Handle paths with spaces correctly
   - Test on different OS (Windows uses backslashes)

2. **Validation Order**
   - Validate paths before using them
   - Don't assume directories exist
   - Check permissions early

3. **Error Messages**
   - Make them user-friendly
   - Include suggestions when possible
   - Don't expose internal implementation details

4. **Sensitive Data**
   - Don't log passwords
   - Mask passwords in toString()
   - Clear passwords from memory when done (future enhancement)

5. **Testing**
   - Use temp directories for tests
   - Clean up test files
   - Test both success and failure paths
   - Test platform-specific code on each platform

---

## Next Steps After Implementation

Once CLI module is complete:
1. Run all tests: `mvn test`
2. Build JAR: `mvn package`
3. Test manually with various arguments
4. Move on to Backup module implementation

---

**End of CLI Implementation Guide**
