# iOS Voicemail to WAV Converter - Complete Specifications

**Version:** 1.0.0
**Date:** 2025-11-08
**Status:** Specification Complete - Ready for Implementation

---

## Table of Contents

1. [Project Overview](#project-overview)
2. [CLI Interface Specification](#1-cli-interface-specification)
3. [iOS Device Detection & Backup Discovery](#2-ios-device-detection--backup-discovery)
4. [Backup Strategy](#3-backup-strategy)
5. [Voicemail Extraction Process](#4-voicemail-extraction-process)
6. [Metadata Parsing & Preservation](#5-metadata-parsing--preservation)
7. [FFmpeg Conversion Process](#6-ffmpeg-conversion-process)
8. [Output File Organization](#7-output-file-organization)
9. [Error Handling & Logging](#8-error-handling--logging)
10. [Implementation Roadmap](#implementation-roadmap)

---

## Project Overview

### Goal
Create a cross-platform Java command-line application that extracts voicemail files from iOS device backups and converts them to WAV format with metadata preservation.

### Key Decisions
- ✅ **Use existing backups** (no backup creation)
- ✅ **Command-line only** (no interactive prompts)
- ✅ **Never delete backups** (read-only operations)
- ✅ **Java 17+** for cross-platform compatibility
- ✅ **FFmpeg** for audio conversion

### Architecture Pipeline
```
User Input → Backup Discovery → Voicemail Extraction → Metadata Parsing → FFmpeg Conversion → Output Organization
```

---

## 1. CLI Interface Specification

### Command Syntax
```bash
java -jar voicemail-converter.jar [OPTIONS]
```

### Arguments

| Flag | Type | Default | Description |
|------|------|---------|-------------|
| `--backup-dir` | Path | Platform default | iOS backup directory (optional override) |
| `--output-dir` | Path | `./voicemail-wavs/` | Output directory for WAV files |
| `--device-id` | String | Auto-select | Device UDID (if multiple backups) |
| `--backup-password` | String | None | Password for encrypted backups |
| `--format` | String | `wav` | Output format (wav, mp3, flac) |
| `--keep-originals` | Flag | false | Copy original AMR files to ./voicemail-backup/ |
| `--include-metadata` | Flag | false | Export metadata as JSON |
| `--verbose` | Flag | false | Detailed logging |
| `--log-file` | Path | None | Write logs to file |
| `--help` | Flag | N/A | Show help |
| `--version` | Flag | N/A | Show version |

### Exit Codes

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
| 8 | Disk space insufficient |

### Example Usage
```bash
# Basic usage (auto-detect backup, outputs to ./voicemail-wavs/)
java -jar voicemail-converter.jar

# Custom output directory
java -jar voicemail-converter.jar \
  --output-dir /Volumes/External/voicemails

# With metadata and originals (creates ./voicemail-backup/)
java -jar voicemail-converter.jar \
  --include-metadata \
  --keep-originals \
  --verbose

# Custom backup location (edge case)
java -jar voicemail-converter.jar \
  --backup-dir ~/CustomBackup/iPhone

# Encrypted backup
java -jar voicemail-converter.jar \
  --backup-password mypassword123
```

---

## 2. iOS Device Detection & Backup Discovery

### Platform-Specific Backup Locations

| Platform | Default Path |
|----------|-------------|
| macOS | `~/Library/Application Support/MobileSync/Backup/` |
| Windows | `%APPDATA%\Apple Computer\MobileSync\Backup\` |
| Linux | `~/.local/share/MobileSync/Backup/` |

### Backup Directory Structure
```
MobileSync/Backup/
└── {UDID}/                    # 40-char hex or UUID format
    ├── Info.plist             # Device metadata
    ├── Manifest.plist         # Backup metadata
    ├── Manifest.db            # SQLite file catalog
    ├── Status.plist           # Backup status
    └── ab/                    # Hashed files
        └── ab1234...          # Actual backup files
```

### Discovery Process
1. Locate backup directory (user-specified or platform default)
2. Enumerate subdirectories matching UDID format
3. Parse `Info.plist` for device information
4. Parse `Manifest.plist` for encryption status
5. Select appropriate backup (auto or by `--device-id`)

### BackupInfo Data Structure
```java
class BackupInfo {
    String udid;              // Device UDID
    String deviceName;        // "John's iPhone"
    String productType;       // "iPhone14,2"
    String iosVersion;        // "17.5.1"
    LocalDateTime backupDate; // Last backup timestamp
    boolean isEncrypted;      // Encryption status
    Path backupPath;          // Full path to backup
}
```

---

## 3. Backup Strategy

### Core Principles
- **Always use existing backups** - Never create new ones
- **Read-only operations** - Never modify backup files
- **Preservation first** - Never delete backups after extraction

### Validation Checklist
- ✓ Backup directory exists and readable
- ✓ `Info.plist`, `Manifest.plist`, `Manifest.db` present
- ✓ iOS version >= 7.0
- ✓ Backup complete (Status.plist check)
- ✓ Encryption handled if necessary
- ✓ SQLite integrity check

### Multiple Backups
- If multiple backups found without `--device-id`: **Error with list**
- Display device name, iOS version, backup date, UDID
- User must specify which backup to use

### Backup Age Warnings
| Age | Action |
|-----|--------|
| < 24 hours | Silent |
| 1-7 days | Info message |
| 7-30 days | Warning message |
| > 30 days | Strong warning |

---

## 4. Voicemail Extraction Process

### iOS Backup File Hashing
Files stored as: `SHA1(domain + "-" + relativePath)`

Example:
- Domain: `Library-Voicemail`
- Path: `voicemail/1699123456.amr`
- Hash: `3d0d7e5fb2ce288813306e4d4636395e047a3d28`
- Location: `3d/3d0d7e5fb2ce288813306e4d4636395e047a3d28`

### Voicemail Locations

| File | Domain | Path |
|------|--------|------|
| Audio files | `Library-Voicemail` | `voicemail/*.amr` |
| Database | `Library-Voicemail` | `voicemail.db` |
| Greeting | `Library-Voicemail` | `greeting.amr` |

### Extraction Flow
1. **Open Manifest.db** - Connect to SQLite database
2. **Query voicemail.db file** - Get hash and extract
3. **Parse voicemail.db** - Read metadata table
4. **Query audio files** - Find all .amr/.awb/.m4a files
5. **Extract to temp** - Copy files from backup
6. **Pair files with metadata** - Match by timestamp

### Data Structures

**VoicemailMetadata:**
```java
class VoicemailMetadata {
    long rowId;
    long remoteUid;
    Instant date;
    String sender;
    String callbackNumber;
    int duration;
    boolean isRead;
    boolean isSpam;
    Instant trashedDate;
}
```

**VoicemailFile:**
```java
class VoicemailFile {
    String fileId;              // SHA1 hash
    String relativePath;        // iOS path
    Path backupFilePath;        // Backup location
    Path extractedPath;         // Temp location
    VoicemailMetadata metadata; // Paired metadata
    AudioFormat format;         // AMR-NB/AMR-WB/AAC
}
```

---

## 5. Metadata Parsing & Preservation

### Metadata Sources
1. **voicemail.db** - Caller, timestamp, duration, status
2. **Manifest.db** - File hashes, paths, sizes
3. **Info.plist** - Device info, backup context
4. **FFprobe** - Audio analysis (format, actual duration)

### Storage Formats

**Option 1: Embedded in WAV (Default)**
```
WAV INFO chunk:
- INAM: Caller name/number
- ICMT: Full metadata comment
- ICRD: Received date
- IGNR: Phone number
- ISFT: Software version
```

**Option 2: Separate JSON (with `--include-metadata`)**
```json
{
  "voicemail": {
    "caller": {
      "phoneNumber": "+1-234-567-8900",
      "displayName": "John Doe"
    },
    "timestamps": {
      "received": "2023-11-04T15:30:56Z"
    },
    "duration": {
      "databaseSeconds": 45,
      "actualMilliseconds": 45234
    },
    "audio": {
      "originalFormat": "AMR-NB",
      "sampleRate": 8000
    },
    "device": {
      "name": "John's iPhone",
      "iosVersion": "17.5.1"
    }
  }
}
```

### Phone Number Normalization
1. Remove non-digit characters (except +)
2. If E.164 format (+12345678900): keep as-is
3. If 10 digits: assume US, prepend +1
4. Store both normalized and display formats

---

## 6. FFmpeg Conversion Process

### Requirements
- **FFmpeg 4.0+** with AMR codec support
- **ffprobe** for input analysis

### Detection
```bash
ffmpeg -version  # Check installation
ffprobe -version # Check availability
```

### Input Format Support

| Format | Codec | Extension | iOS Version | Sample Rate |
|--------|-------|-----------|-------------|-------------|
| AMR-NB | amr_nb | .amr | iOS 7+ | 8 kHz |
| AMR-WB | amr_wb | .awb | iOS 13+ | 16 kHz |
| AAC | aac | .m4a | iOS 16+ | 44.1 kHz |

### Output Specification (WAV)
```
Format:       WAV (RIFF WAVE)
Codec:        PCM signed 16-bit little-endian
Sample Rate:  44100 Hz
Bit Depth:    16-bit
Channels:     1 (Mono)
Bit Rate:     ~705 kbps
```

### Conversion Command
```bash
ffmpeg -i input.amr \
       -ar 44100 \
       -ac 1 \
       -acodec pcm_s16le \
       -metadata title="John Doe" \
       -metadata artist="+1-234-567-8900" \
       -metadata date="2023-11-04" \
       -metadata comment="Duration: 45s, Received: 2023-11-04 15:30:56" \
       -metadata encoded_by="iOS Voicemail Converter v1.0.0" \
       -y \
       output.wav
```

### Progress Tracking
Parse FFmpeg output for time progress:
```
time=00:00:12.34
```
Calculate: `currentSeconds / totalDuration * 100`

### File Size Estimation
```
WAV Size ≈ 44100 Hz × 2 bytes × 1 channel × duration
Example: 45 seconds ≈ 3.8 MB
```

---

## 7. Output File Organization

### Directory Structure

**Default Structure:**
```
./voicemail-wavs/                                       # Converted WAV files
└── 2025-11-08/                                         # Extraction date
    ├── voicemail-2023-11-04T15-30-56-+1234567890.wav
    ├── voicemail-2023-11-04T15-30-56-+1234567890.json  # If --include-metadata
    └── voicemail-2023-11-03T09-15-23-Unknown.wav
```

**With --keep-originals Flag:**
```
./voicemail-wavs/                                       # Converted WAV files
└── 2025-11-08/
    ├── voicemail-2023-11-04T15-30-56-+1234567890.wav
    └── voicemail-2023-11-03T09-15-23-Unknown.wav

./voicemail-backup/                                     # Original AMR files
└── 2025-11-08/                                         # Same date structure
    ├── voicemail-2023-11-04T15-30-56-+1234567890.amr
    └── voicemail-2023-11-03T09-15-23-Unknown.amr
```

### Filename Convention
```
Format: voicemail-{timestamp}-{caller}.{ext}

Components:
  - Prefix: "voicemail-"
  - Timestamp: YYYY-MM-DDTHH-mm-ss (ISO 8601)
  - Caller: Phone number or "Unknown"
  - Extension: .wav, .json, .amr

Examples:
  voicemail-2023-11-04T15-30-56-+1234567890.wav
  voicemail-2023-11-03T09-15-23-Unknown.wav
```

### Filename Sanitization
- Remove invalid filesystem characters
- Convert spaces to underscores
- Truncate caller to 20 characters max
- Handle collisions with sequence numbers (_001, _002)

### Directory Creation
1. Create `voicemail-wavs/` directory if needed (or custom --output-dir)
2. Create date subdirectory inside: `voicemail-wavs/YYYY-MM-DD/`
3. If `--keep-originals`: Create `voicemail-backup/` and `voicemail-backup/YYYY-MM-DD/`
4. Verify write permissions before processing

**Notes:**
- Both `voicemail-wavs/` and `voicemail-backup/` are created in the current working directory by default
- User can change WAV output location with `--output-dir`, but backup folder always mirrors the date structure
- Backup folder is created at the same level as the output directory

---

## 8. Error Handling & Logging

### Log Levels

| Level | Purpose | Default Mode | Verbose Mode |
|-------|---------|--------------|--------------|
| ERROR | Critical failures | ✓ Show | ✓ Show |
| WARN | Non-fatal issues | ✓ Show | ✓ Show |
| INFO | Progress updates | ✗ Hide | ✓ Show |
| DEBUG | Detailed info | ✗ Hide | ✓ Show |

### Console Format
```
[LEVEL] Message

Example:
[ERROR] FFmpeg not found in PATH
[WARN] Backup is 15 days old
[INFO] Found 12 voicemails in backup
```

### Log File Format
```
YYYY-MM-DD HH:mm:ss.SSS [LEVEL] [Component] Message

Example:
2025-11-08 14:32:15.123 [ERROR] [FFmpegConverter] FFmpeg not found
```

### Error Categories

| Category | Exit Code | Recovery |
|----------|-----------|----------|
| Configuration | 2 | User fixes arguments |
| Dependencies | 6 | User installs FFmpeg |
| Backup issues | 3 | User creates backup |
| Encryption | 4 | User provides password |
| Permissions | 7 | User fixes permissions |
| Disk space | 8 | User frees space |
| No data | 5 | Informational |
| Runtime | 1 | Varies |

### Error Recovery Strategy
- **Fail fast**: Config errors, missing dependencies, backup not found
- **Continue**: Single file corruption, single conversion failure
- **Graceful degradation**: Missing metadata, FFprobe unavailable

### User-Friendly Error Messages
```
[ERROR] <What happened>

<Why it happened>

Suggestion:
  <Actionable steps>

<Documentation link>
```

---

## Implementation Roadmap

### Phase 1: Core Functionality (MVP)
1. **CLI Argument Parsing** - Apache Commons CLI
2. **Backup Discovery** - File system navigation, plist parsing
3. **Manifest.db Reading** - SQLite JDBC
4. **Voicemail Extraction** - File I/O, SHA-1 hashing
5. **Metadata Parsing** - SQLite queries, data structures
6. **FFmpeg Integration** - ProcessBuilder, output parsing
7. **WAV Conversion** - Basic conversion to WAV
8. **Output Organization** - File naming, directory structure
9. **Error Handling** - Exception hierarchy, logging
10. **Testing** - Unit tests, integration tests

### Phase 2: Enhanced Features
1. **Metadata Embedding** - WAV INFO chunks
2. **JSON Export** - Comprehensive metadata files
3. **Original File Preservation** - Copy AMR files
4. **Progress Bars** - Visual feedback
5. **Parallel Processing** - Multi-threaded conversion
6. **Encryption Support** - Encrypted backup handling

### Phase 3: Future Enhancements
1. **Additional Formats** - MP3, FLAC support
2. **Audio Normalization** - Volume leveling
3. **Contact Name Resolution** - AddressBook.sqlitedb parsing
4. **Index Generation** - CSV/HTML summaries
5. **GUI Option** - JavaFX interface
6. **Backup Creation** - Optional backup trigger

---

## Technology Stack

### Core
- **Java 17+** - Main language
- **Apache Commons CLI** - Command-line parsing
- **SQLite JDBC** - Database access

### External Dependencies
- **FFmpeg 4.0+** - Audio conversion
- **ffprobe** - Audio analysis

### Libraries (Optional)
- **dd-plist** - plist parsing (or native XML)
- **SLF4J + Logback** - Logging framework
- **JUnit 5** - Testing
- **Mockito** - Mocking for tests

---

## Project Structure

```
voicemail-converter/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/voicemail/
│   │   │       ├── Main.java
│   │   │       ├── cli/
│   │   │       │   ├── CLIParser.java
│   │   │       │   └── Arguments.java
│   │   │       ├── backup/
│   │   │       │   ├── BackupDiscovery.java
│   │   │       │   ├── BackupInfo.java
│   │   │       │   └── BackupReader.java
│   │   │       ├── extractor/
│   │   │       │   ├── VoicemailExtractor.java
│   │   │       │   └── ManifestDbReader.java
│   │   │       ├── metadata/
│   │   │       │   ├── MetadataParser.java
│   │   │       │   ├── VoicemailMetadata.java
│   │   │       │   └── MetadataEmbedder.java
│   │   │       ├── converter/
│   │   │       │   ├── FFmpegConverter.java
│   │   │       │   └── AudioFormat.java
│   │   │       ├── output/
│   │   │       │   ├── FileOrganizer.java
│   │   │       │   └── FilenameGenerator.java
│   │   │       └── util/
│   │   │           ├── Logger.java
│   │   │           └── ExceptionHandler.java
│   │   └── resources/
│   │       └── logback.xml
│   └── test/
│       └── java/
│           └── com/voicemail/
│               └── (test classes)
├── build.gradle (or pom.xml)
├── README.md
├── SPECIFICATIONS.md (this file)
└── LICENSE
```

---

## Next Steps

1. **Review Specifications** - Confirm all requirements are met
2. **Set Up Project** - Initialize Git repo, build system
3. **Implement Phase 1** - Build MVP with core features
4. **Test with Real Data** - Use actual iOS backups
5. **Iterate** - Add Phase 2 features based on testing
6. **Documentation** - User guide, API docs
7. **Release** - Package, distribute, gather feedback

---

## Appendices

### A. iOS Backup Format References
- [libimobiledevice documentation](https://libimobiledevice.org/)
- [iOS backup structure](https://theiphonewiki.com/wiki/ITunes_Backup)

### B. FFmpeg Resources
- [FFmpeg documentation](https://ffmpeg.org/documentation.html)
- [AMR codec guide](https://trac.ffmpeg.org/wiki/Encode/AMR)

### C. Useful Tools
- **DB Browser for SQLite** - Inspect Manifest.db
- **PlistEdit Pro** - View/edit plist files
- **Hex Fiend** - Examine binary files

---

**End of Specifications**
