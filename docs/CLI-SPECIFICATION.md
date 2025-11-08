# CLI Module Specification

**Version:** 1.0.0
**Date:** 2025-11-08
**Status:** Ready for Implementation

---

## Overview

The CLI module is responsible for:
1. Parsing command-line arguments
2. Validating user input
3. Building configuration objects
4. Displaying help and version information
5. Providing user-friendly error messages

---

## Module Components

### 1. CLIParser
**Responsibility:** Parse and validate command-line arguments

**Public API:**
```java
class CLIParser {
    /**
     * Parse command-line arguments into Arguments object
     * @param args Raw command-line arguments
     * @return Parsed and validated Arguments
     * @throws ConfigurationException if arguments are invalid
     */
    public Arguments parse(String[] args) throws ConfigurationException;

    /**
     * Display help message and exit
     */
    public void printHelp();

    /**
     * Display version information and exit
     */
    public void printVersion();
}
```

### 2. Arguments
**Responsibility:** Immutable configuration object containing all parsed arguments

**Public API:**
```java
class Arguments {
    // Core paths
    public Path getBackupDir();
    public Path getOutputDir();

    // Backup selection
    public Optional<String> getDeviceId();
    public Optional<String> getBackupPassword();

    // Output options
    public AudioFormat getFormat();
    public boolean isKeepOriginals();
    public boolean isIncludeMetadata();

    // Logging
    public boolean isVerbose();
    public Optional<Path> getLogFile();

    // Builder pattern for construction
    public static class Builder { ... }
}
```

### 3. HelpFormatter
**Responsibility:** Format and display help text

**Public API:**
```java
class HelpFormatter {
    /**
     * Format help text
     * @return Formatted help string
     */
    public String formatHelp();

    /**
     * Format usage text
     * @return Formatted usage string
     */
    public String formatUsage();

    /**
     * Format examples
     * @return Formatted examples string
     */
    public String formatExamples();
}
```

---

## Command-Line Arguments Specification

### Argument Definitions

| Short | Long | Type | Required | Default | Description |
|-------|------|------|----------|---------|-------------|
| `-b` | `--backup-dir` | Path | No | Platform default | iOS backup directory |
| `-o` | `--output-dir` | Path | No | `./voicemail-wavs/` | Output directory |
| `-d` | `--device-id` | String | No | Auto-select | Device UDID |
| `-p` | `--backup-password` | String | No | None | Encrypted backup password |
| `-f` | `--format` | String | No | `wav` | Output format |
| | `--keep-originals` | Flag | No | false | Copy original files |
| | `--include-metadata` | Flag | No | false | Export JSON metadata |
| `-v` | `--verbose` | Flag | No | false | Verbose logging |
| `-l` | `--log-file` | Path | No | None | Log file path |
| `-h` | `--help` | Flag | No | N/A | Show help |
| | `--version` | Flag | No | N/A | Show version |

### Argument Details

#### --backup-dir (-b)
```yaml
Type: Path (directory)
Required: false
Default: Platform-specific
  - macOS: ~/Library/Application Support/MobileSync/Backup/
  - Windows: %APPDATA%\Apple Computer\MobileSync\Backup\
  - Linux: ~/.local/share/MobileSync/Backup/

Validation:
  - If provided, must be an existing directory
  - Must be readable
  - Will be resolved to absolute path

Examples:
  --backup-dir ~/Backups/iPhone
  -b /Volumes/External/MobileSync/Backup
```

#### --output-dir (-o)
```yaml
Type: Path (directory)
Required: false
Default: ./voicemail-wavs/

Validation:
  - If exists, must be a directory
  - If doesn't exist, will be created
  - Parent directory must be writable
  - Will be resolved to absolute path

Examples:
  --output-dir ~/Desktop/Voicemails
  -o /Volumes/External/voicemails
```

#### --device-id (-d)
```yaml
Type: String (UDID format)
Required: false (required if multiple backups exist)
Default: Auto-select if single backup

Validation:
  - Must be 40 hexadecimal characters OR UUID format
  - Pattern: ^[0-9a-fA-F]{40}$ or UUID pattern

Examples:
  --device-id 00008030001E4D8A3602802E001234567890ABCD
  -d 12345678-1234-1234-1234-123456789012
```

#### --backup-password (-p)
```yaml
Type: String
Required: false (required if backup is encrypted)
Default: None

Validation:
  - Can be any string
  - Will not be logged
  - Will be cleared from memory after use

Security Warning:
  Display warning about command-line password visibility

Examples:
  --backup-password MySecurePassword123
  -p "password with spaces"
```

#### --format (-f)
```yaml
Type: String (enum)
Required: false
Default: wav
Allowed Values: wav, mp3, flac (Phase 1: wav only)

Validation:
  - Must be one of allowed values (case-insensitive)

Examples:
  --format wav
  -f WAV
```

#### --keep-originals
```yaml
Type: Boolean flag
Required: false
Default: false

Behavior:
  - If present: Copy original AMR files to ./voicemail-backup/
  - If absent: Only converted WAV files are saved

Examples:
  --keep-originals
```

#### --include-metadata
```yaml
Type: Boolean flag
Required: false
Default: false

Behavior:
  - If present: Export metadata as JSON files alongside WAV files
  - If absent: Only WAV files (with embedded metadata) are saved

Examples:
  --include-metadata
```

#### --verbose (-v)
```yaml
Type: Boolean flag
Required: false
Default: false

Behavior:
  - If present: Enable DEBUG level logging to console
  - If absent: Only WARN and ERROR to console

Examples:
  --verbose
  -v
```

#### --log-file (-l)
```yaml
Type: Path (file)
Required: false
Default: ~/.voicemail-converter/logs/voicemail-converter-{timestamp}.log

Validation:
  - Parent directory must be writable
  - Will create file if doesn't exist
  - Will append if exists

Examples:
  --log-file ~/Desktop/conversion.log
  -l /tmp/voicemail.log
```

#### --help (-h)
```yaml
Type: Boolean flag
Required: false

Behavior:
  - Display help text
  - Exit with code 0
  - Ignore all other arguments

Examples:
  --help
  -h
```

#### --version
```yaml
Type: Boolean flag
Required: false

Behavior:
  - Display version information
  - Exit with code 0
  - Ignore all other arguments

Examples:
  --version
```

---

## Parsing Logic

### Parsing Flow
```
1. Check for --help or --version first
   → If present: Display and exit immediately

2. Parse all arguments using Apache Commons CLI
   → Build Options object with all definitions

3. Extract values from CommandLine object

4. Validate each argument
   → Path validation
   → Format validation
   → Pattern matching

5. Apply defaults for missing arguments
   → Platform-specific backup dir
   → Current directory for output

6. Build Arguments object using Builder pattern

7. Return Arguments object
```

### Validation Rules

#### Path Validation
```java
void validatePath(Path path, PathType type) {
    switch (type) {
        case EXISTING_DIRECTORY:
            if (!Files.exists(path))
                throw new ConfigurationException("Directory does not exist: " + path);
            if (!Files.isDirectory(path))
                throw new ConfigurationException("Not a directory: " + path);
            if (!Files.isReadable(path))
                throw new ConfigurationException("Directory not readable: " + path);
            break;

        case OUTPUT_DIRECTORY:
            if (Files.exists(path) && !Files.isDirectory(path))
                throw new ConfigurationException("Output path exists but is not a directory: " + path);
            // Check parent directory is writable
            Path parent = path.getParent();
            if (parent != null && !Files.isWritable(parent))
                throw new ConfigurationException("Cannot create directory (parent not writable): " + path);
            break;

        case FILE:
            // For log files
            if (Files.exists(path) && Files.isDirectory(path))
                throw new ConfigurationException("Path is a directory, not a file: " + path);
            break;
    }
}
```

#### UDID Validation
```java
void validateUdid(String udid) {
    // Check for 40-character hex format
    Pattern hex40 = Pattern.compile("^[0-9a-fA-F]{40}$");

    // Check for UUID format
    Pattern uuid = Pattern.compile(
        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    );

    if (!hex40.matcher(udid).matches() && !uuid.matcher(udid).matches()) {
        throw new ConfigurationException(
            "Invalid device ID format. Expected 40 hex characters or UUID format."
        );
    }
}
```

#### Format Validation
```java
void validateFormat(String format) {
    Set<String> validFormats = Set.of("wav", "mp3", "flac");

    if (!validFormats.contains(format.toLowerCase())) {
        throw new ConfigurationException(
            "Invalid format: " + format + ". Allowed: " + validFormats
        );
    }

    // Phase 1: Only WAV supported
    if (!"wav".equalsIgnoreCase(format)) {
        throw new ConfigurationException(
            "Only WAV format is currently supported. MP3 and FLAC coming in future version."
        );
    }
}
```

---

## Help Text Specification

### Help Text Format
```
iOS Voicemail Converter - Extract and convert iOS voicemails from backups

USAGE:
    java -jar voicemail-converter.jar [OPTIONS]

OPTIONS:
    -b, --backup-dir <path>         iOS backup directory (default: auto-detect)
    -o, --output-dir <path>         Output directory (default: ./voicemail-wavs/)
    -d, --device-id <udid>          Target specific device UDID
    -p, --backup-password <pass>    Password for encrypted backup
    -f, --format <format>           Output format: wav (default: wav)
        --keep-originals            Copy original AMR files to ./voicemail-backup/
        --include-metadata          Export metadata as JSON files
    -v, --verbose                   Enable detailed logging
    -l, --log-file <path>           Write logs to file
    -h, --help                      Show this help message
        --version                   Show version information

EXAMPLES:
    # Basic usage (auto-detect backup, output to ./voicemail-wavs/)
    java -jar voicemail-converter.jar

    # With verbose output and original files
    java -jar voicemail-converter.jar --verbose --keep-originals

    # Custom directories
    java -jar voicemail-converter.jar -b ~/Backups -o ~/Desktop/VM

    # Encrypted backup with metadata export
    java -jar voicemail-converter.jar -p mypassword --include-metadata

    # Specific device
    java -jar voicemail-converter.jar -d 00008030001E4D8A3602802E

REQUIREMENTS:
    - Java 17 or higher
    - FFmpeg installed and in PATH
    - Existing iOS backup (created via iTunes/Finder)

For more information: https://github.com/yourusername/voicemail-converter
```

### Version Text Format
```
iOS Voicemail Converter v1.0.0

Java Version:  17.0.1
Java VM:       OpenJDK 64-Bit Server VM
OS:            macOS 14.5
Architecture:  aarch64

Copyright (c) 2025 iOS Voicemail Converter Contributors
Licensed under MIT License

For help: java -jar voicemail-converter.jar --help
```

---

## Error Messages

### Invalid Argument
```
Error: Invalid argument: --invalid-flag

Usage: java -jar voicemail-converter.jar [OPTIONS]
Run with --help for more information

Exit Code: 2
```

### Missing Required Value
```
Error: Option '--backup-dir' requires a value

Usage: java -jar voicemail-converter.jar --backup-dir <path>
Example: java -jar voicemail-converter.jar --backup-dir ~/Backups

Exit Code: 2
```

### Invalid Path
```
Error: Backup directory does not exist: /path/that/does/not/exist

Suggestion:
  1. Check the path is correct
  2. Use auto-detection by omitting --backup-dir
  3. Create an iOS backup first via iTunes/Finder

Exit Code: 2
```

### Invalid UDID Format
```
Error: Invalid device ID format: abc123

Expected format:
  - 40 hexadecimal characters, OR
  - UUID format (8-4-4-4-12)

Example: 00008030001E4D8A3602802E001234567890ABCD

Exit Code: 2
```

### Unsupported Format
```
Error: Format 'mp3' is not yet supported

Currently supported formats:
  - wav (uncompressed PCM)

Coming soon: mp3, flac

Exit Code: 2
```

### Password Visibility Warning
```
Warning: Password provided via command-line is visible in process list

Consider:
  - Using environment variable: BACKUP_PASSWORD
  - Disabling backup encryption in iTunes/Finder
  - Creating unencrypted backup for this operation

Continue? [y/N]
```

---

## Usage Examples

### Example 1: Basic Usage
```bash
java -jar voicemail-converter.jar
```
**Expected behavior:**
- Auto-detect backup location
- If single backup: Use it
- If multiple: Error with list
- Output to ./voicemail-wavs/YYYY-MM-DD/

### Example 2: Verbose with Originals
```bash
java -jar voicemail-converter.jar -v --keep-originals
```
**Expected behavior:**
- Show detailed INFO and DEBUG logs
- Create ./voicemail-wavs/ with WAV files
- Create ./voicemail-backup/ with AMR files

### Example 3: Custom Directories
```bash
java -jar voicemail-converter.jar \
  --backup-dir ~/CustomBackup \
  --output-dir /Volumes/External/VM
```
**Expected behavior:**
- Read from ~/CustomBackup
- Output to /Volumes/External/VM/YYYY-MM-DD/

### Example 4: Full Options
```bash
java -jar voicemail-converter.jar \
  --backup-dir ~/Backups \
  --output-dir ~/Desktop/Voicemails \
  --device-id 00008030001E4D8A3602802E \
  --keep-originals \
  --include-metadata \
  --verbose \
  --log-file ~/Desktop/conversion.log
```
**Expected behavior:**
- Use specific backup and device
- Keep originals in separate folder
- Export JSON metadata
- Verbose console output
- Write detailed log to file

---

## Implementation Notes

### Apache Commons CLI Integration
```java
Options options = new Options();

// Add options
options.addOption(Option.builder("b")
    .longOpt("backup-dir")
    .hasArg()
    .argName("path")
    .desc("iOS backup directory")
    .build());

options.addOption(Option.builder("o")
    .longOpt("output-dir")
    .hasArg()
    .argName("path")
    .desc("Output directory")
    .build());

// ... add all options

CommandLineParser parser = new DefaultParser();
CommandLine cmd = parser.parse(options, args);
```

### Builder Pattern for Arguments
```java
Arguments.Builder builder = new Arguments.Builder();

if (cmd.hasOption("backup-dir")) {
    builder.backupDir(Paths.get(cmd.getOptionValue("backup-dir")));
} else {
    builder.backupDir(detectDefaultBackupDir());
}

if (cmd.hasOption("output-dir")) {
    builder.outputDir(Paths.get(cmd.getOptionValue("output-dir")));
} else {
    builder.outputDir(Paths.get("./voicemail-wavs/"));
}

// ... set all options

Arguments args = builder.build();
```

### Platform Detection for Defaults
```java
Path detectDefaultBackupDir() {
    String os = System.getProperty("os.name").toLowerCase();
    String home = System.getProperty("user.home");

    if (os.contains("mac")) {
        return Paths.get(home, "Library/Application Support/MobileSync/Backup");
    } else if (os.contains("win")) {
        String appData = System.getenv("APPDATA");
        return Paths.get(appData, "Apple Computer/MobileSync/Backup");
    } else {
        // Linux
        return Paths.get(home, ".local/share/MobileSync/Backup");
    }
}
```

---

## Testing Checklist

### Unit Tests
- [ ] Parse valid arguments successfully
- [ ] Reject invalid arguments with proper errors
- [ ] Apply defaults correctly
- [ ] Validate paths correctly
- [ ] Validate UDID format
- [ ] Validate format enum
- [ ] Handle --help flag
- [ ] Handle --version flag
- [ ] Build Arguments object correctly
- [ ] Platform detection works on each OS

### Integration Tests
- [ ] End-to-end parsing with real arguments
- [ ] Error messages display correctly
- [ ] Help text displays correctly
- [ ] Version text displays correctly

### Edge Cases
- [ ] Empty arguments array
- [ ] Only --help or --version
- [ ] Paths with spaces
- [ ] Paths with special characters
- [ ] Relative vs absolute paths
- [ ] Missing parent directories
- [ ] Conflicting options (if any)
- [ ] Very long paths
- [ ] Unicode in paths

---

## Dependencies

### Required Libraries
- **Apache Commons CLI 1.6.0** - Command-line parsing
- **SLF4J 2.0.9** - Logging facade

### Standard Library Usage
- `java.nio.file.*` - Path validation and manipulation
- `java.util.regex.Pattern` - UDID validation
- `System.getProperty()` - Platform detection

---

**End of CLI Specification**
