# iOS Voicemail Converter

Extract and convert iOS voicemails from iTunes/Finder backups to WAV format.

## Overview

This command-line tool reads iOS device backups created by iTunes or Finder, extracts voicemail audio files and metadata, and converts them to standard WAV format. The tool preserves caller information and timestamps in the output files.

## Features

- ✅ Extract voicemails from iOS backups (no device connection required)
- ✅ Convert AMR/AMR-WB audio to uncompressed WAV format
- ✅ Preserve metadata (caller, timestamp, duration)
- ✅ Organize output by extraction date
- ✅ Optional JSON metadata export
- ✅ Optional original file preservation
- ✅ Cross-platform support (macOS, Windows, Linux)

## Requirements

### System Requirements
- **Java 17 or higher** - [Download Java](https://adoptium.net/)
- **FFmpeg 4.0+** - Required for audio conversion
- **iOS Backup** - Created via iTunes (Windows) or Finder (macOS)

### Installing FFmpeg

**macOS:**
```bash
brew install ffmpeg
```

**Ubuntu/Debian:**
```bash
sudo apt install ffmpeg
```

**Windows:**
Download from [ffmpeg.org](https://ffmpeg.org/download.html) and add to PATH.

Verify installation:
```bash
ffmpeg -version
```

## Building from Source

### Prerequisites
- Java Development Kit (JDK) 17+
- Maven 3.6+

### Build Steps

1. **Clone the repository:**
   ```bash
   git clone https://github.com/yourusername/voicemail-converter.git
   cd voicemail-converter
   ```

2. **Build with Maven:**
   ```bash
   mvn clean package
   ```

3. **The executable JAR will be created at:**
   ```
   target/voicemail-converter.jar
   ```

### Running Tests
```bash
mvn test
```

### Cleaning Build
```bash
mvn clean
```

## Usage

### Basic Usage

Convert all voicemails using default settings:
```bash
java -jar target/voicemail-converter.jar
```

This will:
- Auto-detect iOS backup location
- Extract voicemails
- Convert to WAV format
- Save to `./voicemail-wavs/YYYY-MM-DD/`

### Command-Line Options

```
java -jar voicemail-converter.jar [OPTIONS]

Options:
  --backup-dir <path>        iOS backup directory (optional override)
  --output-dir <path>        Output directory (default: ./voicemail-wavs/)
  --device-id <udid>         Target specific device UDID
  --backup-password <pass>   Password for encrypted backups
  --format <format>          Output format: wav (default)
  --keep-originals           Copy original AMR files to ./voicemail-backup/
  --include-metadata         Export metadata as JSON files
  --verbose                  Enable detailed logging
  --log-file <path>          Write logs to file
  --help                     Show help message
  --version                  Show version information
```

### Examples

**Convert with verbose output:**
```bash
java -jar voicemail-converter.jar --verbose
```

**Keep original files and export metadata:**
```bash
java -jar voicemail-converter.jar --keep-originals --include-metadata
```

**Custom output directory:**
```bash
java -jar voicemail-converter.jar --output-dir /Volumes/External/Voicemails
```

**Encrypted backup:**
```bash
java -jar voicemail-converter.jar --backup-password mypassword123
```

**Specific device (if multiple backups exist):**
```bash
java -jar voicemail-converter.jar --device-id 00008030-001E4D8A3602802E
```

## Output Structure

### Default Output
```
./voicemail-wavs/
└── 2025-11-08/
    ├── voicemail-2023-11-04T15-30-56-+1234567890.wav
    ├── voicemail-2023-11-03T09-15-23-Unknown.wav
    └── voicemail-2023-11-02T14-22-10-+9876543210.wav
```

### With --keep-originals
```
./voicemail-wavs/
└── 2025-11-08/
    └── (WAV files)

./voicemail-backup/
└── 2025-11-08/
    └── (original AMR files)
```

### With --include-metadata
```
./voicemail-wavs/
└── 2025-11-08/
    ├── voicemail-2023-11-04T15-30-56-+1234567890.wav
    └── voicemail-2023-11-04T15-30-56-+1234567890.json
```

## Backup Locations

The tool automatically detects iOS backups in platform-specific locations:

| Platform | Backup Location |
|----------|----------------|
| macOS | `~/Library/Application Support/MobileSync/Backup/` |
| Windows | `%APPDATA%\Apple Computer\MobileSync\Backup\` |
| Linux | `~/.local/share/MobileSync/Backup/` |

### Creating an iOS Backup

**macOS (Finder):**
1. Connect iPhone to Mac
2. Open Finder
3. Select iPhone in sidebar
4. Click "Back Up Now"

**Windows (iTunes):**
1. Connect iPhone to PC
2. Open iTunes
3. Select device
4. Click "Back Up Now"

**Important:** Ensure "Encrypt local backup" is disabled, or provide the password with `--backup-password`.

## Troubleshooting

### FFmpeg not found
```
Error: FFmpeg is not installed or not in your PATH
```
**Solution:** Install FFmpeg and ensure it's in your system PATH.

### Backup not found
```
Error: No backup directory found
```
**Solution:** Create an iOS backup first, or specify custom location with `--backup-dir`.

### Multiple backups found
```
Error: Multiple backups found. Please specify device with --device-id
```
**Solution:** Use `--device-id` flag with the UDID shown in the error message.

### Encrypted backup
```
Error: Backup is encrypted. Please provide --backup-password
```
**Solution:** Provide backup password or create unencrypted backup.

### No voicemails found
```
Error: No voicemails found in backup
```
**Solution:** This means the backup contains no voicemail data. Ensure voicemails exist on device when backup is created.

## Exit Codes

| Code | Meaning |
|------|---------|
| 0 | Success |
| 1 | General error |
| 2 | Invalid arguments |
| 3 | Backup not found |
| 4 | Backup encrypted (password required) |
| 5 | No voicemails found |
| 6 | FFmpeg not available |
| 7 | Permission denied |
| 8 | Insufficient disk space |

## Development

### Project Structure
```
voicemail-converter/
├── src/
│   ├── main/
│   │   ├── java/com/voicemail/
│   │   │   ├── cli/              # Command-line interface
│   │   │   ├── backup/           # Backup discovery & validation
│   │   │   ├── extractor/        # Voicemail extraction
│   │   │   ├── metadata/         # Metadata parsing
│   │   │   ├── converter/        # Audio conversion
│   │   │   ├── output/           # File organization
│   │   │   ├── util/             # Utilities
│   │   │   └── exception/        # Custom exceptions
│   │   └── resources/
│   │       └── logback.xml       # Logging configuration
│   └── test/
│       └── java/com/voicemail/   # Unit tests
├── pom.xml                        # Maven configuration
├── SPECIFICATIONS.md              # Detailed specifications
├── ARCHITECTURE.md                # Architecture diagrams
└── README.md                      # This file
```

### Dependencies
- **Apache Commons CLI** - Command-line parsing
- **SQLite JDBC** - Database access
- **dd-plist** - Property list parsing
- **SLF4J + Logback** - Logging

### Running in IDE

**IntelliJ IDEA:**
1. Import project as Maven project
2. Set JDK to 17+
3. Run `Main.java`

**Eclipse:**
1. File → Import → Maven → Existing Maven Projects
2. Select project directory
3. Right-click `Main.java` → Run As → Java Application

**VS Code:**
1. Install "Extension Pack for Java"
2. Open project folder
3. Run/Debug `Main.java`

## Contributing

Contributions are welcome! Please:
1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests
5. Submit a pull request

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Acknowledgments

- [libimobiledevice](https://libimobiledevice.org/) - iOS backup format documentation
- [FFmpeg](https://ffmpeg.org/) - Audio conversion
- [dd-plist](https://github.com/3breadt/dd-plist) - Property list parsing

## Support

For issues, questions, or contributions:
- **Issues:** [GitHub Issues](https://github.com/yourusername/voicemail-converter/issues)
- **Discussions:** [GitHub Discussions](https://github.com/yourusername/voicemail-converter/discussions)
- **Documentation:** See [SPECIFICATIONS.md](SPECIFICATIONS.md) for detailed specs

---

**Note:** This tool only reads from backups and never modifies or deletes them. Your iOS backup files are always preserved.
