# iOS Voicemail Converter - Architecture Diagrams

**Version:** 1.0.0
**Date:** 2025-11-08

This document provides visual representations of the system architecture from multiple perspectives.

---

## Table of Contents

1. [System Context Diagram](#1-system-context-diagram)
2. [High-Level Architecture](#2-high-level-architecture)
3. [Component Architecture](#3-component-architecture)
4. [Data Flow Diagram](#4-data-flow-diagram)
5. [Class Structure](#5-class-structure)
6. [Sequence Diagrams](#6-sequence-diagrams)
7. [Deployment View](#7-deployment-view)

---

## 1. System Context Diagram

Shows how the system interacts with external entities.

```mermaid
graph TB
    User[User<br/>Command Line]
    System[iOS Voicemail<br/>Converter]
    Backup[iOS Backup<br/>Files]
    FFmpeg[FFmpeg<br/>External Tool]
    Output[Output<br/>Directory]

    User -->|Commands & Arguments| System
    System -->|Read| Backup
    System -->|Execute| FFmpeg
    System -->|Write| Output
    System -->|Status & Errors| User

    style System fill:#4A90E2,stroke:#2E5C8A,color:#fff
    style User fill:#50C878,stroke:#2E7D4E,color:#fff
    style Backup fill:#FFB84D,stroke:#CC8800,color:#fff
    style FFmpeg fill:#FF6B6B,stroke:#CC5555,color:#fff
    style Output fill:#A78BFA,stroke:#7C3AED,color:#fff
```

**External Dependencies:**
- **User**: Provides command-line arguments and monitors progress
- **iOS Backup Files**: Read-only access to iTunes/Finder backups
- **FFmpeg**: External process for audio conversion
- **Output Directory**: Filesystem location for converted files

---

## 2. High-Level Architecture

Three-tier architecture showing logical layers.

```mermaid
graph TB
    subgraph "Presentation Layer"
        CLI[CLI Parser]
        Logger[Logger]
        Progress[Progress Reporter]
    end

    subgraph "Business Logic Layer"
        BackupMgr[Backup Manager]
        Extractor[Voicemail Extractor]
        MetadataParser[Metadata Parser]
        Converter[Audio Converter]
        Organizer[File Organizer]
    end

    subgraph "Data Access Layer"
        FileReader[File Reader]
        DBReader[Database Reader]
        PlistReader[Plist Reader]
        FFmpegWrapper[FFmpeg Wrapper]
        FileWriter[File Writer]
    end

    subgraph "External Systems"
        BackupFS[iOS Backup<br/>Filesystem]
        FFmpegBin[FFmpeg<br/>Binary]
        OutputFS[Output<br/>Filesystem]
    end

    CLI --> BackupMgr
    CLI --> Logger
    BackupMgr --> Extractor
    Extractor --> MetadataParser
    MetadataParser --> Converter
    Converter --> Organizer

    BackupMgr --> PlistReader
    Extractor --> DBReader
    Extractor --> FileReader
    Converter --> FFmpegWrapper
    Organizer --> FileWriter

    PlistReader --> BackupFS
    DBReader --> BackupFS
    FileReader --> BackupFS
    FFmpegWrapper --> FFmpegBin
    FileWriter --> OutputFS

    Logger -.-> Progress
    Progress -.-> CLI

    style CLI fill:#4A90E2,stroke:#2E5C8A,color:#fff
    style BackupMgr fill:#50C878,stroke:#2E7D4E,color:#fff
    style Extractor fill:#50C878,stroke:#2E7D4E,color:#fff
    style MetadataParser fill:#50C878,stroke:#2E7D4E,color:#fff
    style Converter fill:#50C878,stroke:#2E7D4E,color:#fff
    style Organizer fill:#50C878,stroke:#2E7D4E,color:#fff
```

---

## 3. Component Architecture

Detailed component diagram showing modules and their interactions.

```mermaid
graph TB
    subgraph "CLI Module"
        CLIParser[CLI Parser<br/>Arguments, Validation]
        ErrorHandler[Error Handler<br/>Exit Codes, Messages]
    end

    subgraph "Backup Module"
        BackupDiscovery[Backup Discovery<br/>Locate, Enumerate]
        BackupValidator[Backup Validator<br/>Check Integrity]
        EncryptionHandler[Encryption Handler<br/>Password Decrypt]
    end

    subgraph "Extraction Module"
        ManifestReader[Manifest DB Reader<br/>Query File Catalog]
        VoicemailDBReader[Voicemail DB Reader<br/>Parse Metadata]
        FileExtractor[File Extractor<br/>SHA-1 Hash, Copy]
        FileMatcher[File Matcher<br/>Pair Audio+Metadata]
    end

    subgraph "Metadata Module"
        MetadataParser[Metadata Parser<br/>Parse voicemail.db]
        PhoneFormatter[Phone Formatter<br/>Normalize Numbers]
        MetadataEmbedder[Metadata Embedder<br/>WAV Chunks]
        JSONExporter[JSON Exporter<br/>Metadata Files]
    end

    subgraph "Conversion Module"
        FFmpegDetector[FFmpeg Detector<br/>Check Installation]
        AudioAnalyzer[Audio Analyzer<br/>FFprobe Integration]
        FormatConverter[Format Converter<br/>AMR → WAV]
        ProgressTracker[Progress Tracker<br/>Parse FFmpeg Output]
    end

    subgraph "Output Module"
        FilenameGenerator[Filename Generator<br/>Naming Convention]
        DirectoryCreator[Directory Creator<br/>Date-based Structure]
        FileOrganizer[File Organizer<br/>Copy & Arrange]
        OriginalKeeper[Original Keeper<br/>Preserve AMR Files]
    end

    subgraph "Utility Module"
        Logger[Logger<br/>Multi-level Logging]
        TempManager[Temp Manager<br/>Temp Directory]
        ValidationUtil[Validation Util<br/>Sanitization, Checks]
    end

    CLIParser --> BackupDiscovery
    BackupDiscovery --> BackupValidator
    BackupValidator --> EncryptionHandler

    BackupValidator --> ManifestReader
    ManifestReader --> VoicemailDBReader
    ManifestReader --> FileExtractor
    VoicemailDBReader --> MetadataParser
    FileExtractor --> FileMatcher
    MetadataParser --> FileMatcher

    FileMatcher --> PhoneFormatter
    PhoneFormatter --> AudioAnalyzer
    AudioAnalyzer --> FormatConverter
    FormatConverter --> MetadataEmbedder
    MetadataEmbedder --> JSONExporter

    JSONExporter --> FilenameGenerator
    FilenameGenerator --> DirectoryCreator
    DirectoryCreator --> FileOrganizer
    FileOrganizer --> OriginalKeeper

    FFmpegDetector --> FormatConverter
    ProgressTracker --> Logger
    TempManager --> FileExtractor
    ValidationUtil --> FilenameGenerator
    ErrorHandler -.-> Logger

    style CLIParser fill:#4A90E2,stroke:#2E5C8A,color:#fff
    style BackupDiscovery fill:#50C878,stroke:#2E7D4E,color:#fff
    style ManifestReader fill:#FFB84D,stroke:#CC8800,color:#fff
    style MetadataParser fill:#FF6B6B,stroke:#CC5555,color:#fff
    style FormatConverter fill:#A78BFA,stroke:#7C3AED,color:#fff
    style FileOrganizer fill:#EC4899,stroke:#BE185D,color:#fff
```

---

## 4. Data Flow Diagram

Shows how data moves through the system.

```mermaid
flowchart LR
    subgraph Input
        CmdArgs[Command-Line<br/>Arguments]
        BackupFiles[iOS Backup<br/>Files]
    end

    subgraph Processing
        Parse[Parse<br/>Arguments]
        Discover[Discover<br/>Backups]
        Extract[Extract<br/>Voicemails]
        ParseMeta[Parse<br/>Metadata]
        Convert[Convert<br/>Audio]
        Organize[Organize<br/>Output]
    end

    subgraph Output
        WAVFiles[WAV<br/>Files]
        JSONFiles[JSON<br/>Files]
        OrigFiles[Original<br/>AMR Files]
        Logs[Log<br/>Files]
    end

    CmdArgs --> Parse
    Parse --> Discover
    BackupFiles --> Discover
    Discover --> Extract
    BackupFiles --> Extract
    Extract --> ParseMeta
    ParseMeta --> Convert
    Convert --> Organize
    Organize --> WAVFiles
    Organize --> JSONFiles
    Organize --> OrigFiles
    Parse --> Logs

    style Parse fill:#4A90E2,stroke:#2E5C8A,color:#fff
    style Discover fill:#50C878,stroke:#2E7D4E,color:#fff
    style Extract fill:#FFB84D,stroke:#CC8800,color:#fff
    style ParseMeta fill:#FF6B6B,stroke:#CC5555,color:#fff
    style Convert fill:#A78BFA,stroke:#7C3AED,color:#fff
    style Organize fill:#EC4899,stroke:#BE185D,color:#fff
```

**Data Transformations:**

1. **Command Args** → **Config Object** (validation, defaults)
2. **Backup Directory** → **BackupInfo Object** (discovery, parsing)
3. **Manifest.db** → **File Hash List** (SQLite query)
4. **Hashed Files** → **AMR Audio Files** (extraction to temp)
5. **voicemail.db** → **VoicemailMetadata Objects** (parsing)
6. **AMR Files + Metadata** → **VoicemailFile Objects** (pairing)
7. **AMR Audio** → **WAV Audio** (FFmpeg conversion)
8. **VoicemailFile** → **Structured Output** (organization)

---

## 5. Class Structure

High-level class diagram showing key classes and relationships.

```mermaid
classDiagram
    class Main {
        +main(String[] args)
        -validateEnvironment()
        -setupLogging()
    }

    class VoicemailConverter {
        +run(Arguments args)
        -BackupManager backupManager
        -VoicemailExtractor extractor
        -AudioConverter converter
        -FileOrganizer organizer
    }

    class Arguments {
        +Path backupDir
        +Path outputDir
        +String deviceId
        +String password
        +boolean verbose
        +boolean keepOriginals
        +boolean includeMetadata
    }

    class BackupManager {
        +discoverBackups(Path dir)
        +selectBackup(List~BackupInfo~ backups)
        +validateBackup(BackupInfo backup)
    }

    class BackupInfo {
        +String udid
        +String deviceName
        +String iosVersion
        +LocalDateTime backupDate
        +boolean isEncrypted
        +Path backupPath
    }

    class VoicemailExtractor {
        +extractVoicemails(BackupInfo backup)
        -ManifestDbReader manifestReader
        -FileExtractor fileExtractor
        +List~VoicemailFile~ voicemails
    }

    class VoicemailFile {
        +String fileId
        +Path originalPath
        +Path extractedPath
        +VoicemailMetadata metadata
        +AudioFormat format
    }

    class VoicemailMetadata {
        +long rowId
        +Instant receivedDate
        +String callerNumber
        +String callerName
        +int durationSeconds
        +boolean isRead
        +boolean isSpam
    }

    class AudioConverter {
        +convert(VoicemailFile input)
        +FFmpegWrapper ffmpeg
        +MetadataEmbedder embedder
    }

    class FFmpegWrapper {
        +detectFFmpeg()
        +analyzeAudio(Path file)
        +convertToWav(Path input, Path output)
        +embedMetadata(Path file, Map metadata)
    }

    class FileOrganizer {
        +organizeOutput(List~VoicemailFile~ files)
        -FilenameGenerator nameGen
        -DirectoryCreator dirCreator
    }

    class Logger {
        +error(String msg)
        +warn(String msg)
        +info(String msg)
        +debug(String msg)
    }

    Main --> VoicemailConverter
    VoicemailConverter --> Arguments
    VoicemailConverter --> BackupManager
    VoicemailConverter --> VoicemailExtractor
    VoicemailConverter --> AudioConverter
    VoicemailConverter --> FileOrganizer

    BackupManager --> BackupInfo
    VoicemailExtractor --> VoicemailFile
    VoicemailFile --> VoicemailMetadata
    AudioConverter --> FFmpegWrapper
    AudioConverter --> VoicemailFile
    FileOrganizer --> VoicemailFile

    VoicemailConverter --> Logger
    BackupManager --> Logger
    VoicemailExtractor --> Logger
    AudioConverter --> Logger
    FileOrganizer --> Logger
```

---

## 6. Sequence Diagrams

### 6.1 Main Execution Flow

```mermaid
sequenceDiagram
    actor User
    participant Main
    participant CLI
    participant Converter
    participant BackupMgr as Backup Manager
    participant Extractor
    participant AudioConv as Audio Converter
    participant Organizer

    User->>Main: java -jar voicemail-converter.jar --verbose
    Main->>CLI: parse(args)
    CLI-->>Main: Arguments

    Main->>Converter: run(Arguments)

    Converter->>BackupMgr: discoverBackups()
    BackupMgr-->>Converter: List<BackupInfo>

    Converter->>BackupMgr: selectBackup()
    BackupMgr-->>Converter: BackupInfo

    Converter->>Extractor: extractVoicemails(BackupInfo)
    Extractor-->>Converter: List<VoicemailFile>

    loop For each voicemail
        Converter->>AudioConv: convert(VoicemailFile)
        AudioConv-->>Converter: ConversionResult
    end

    Converter->>Organizer: organizeOutput(List<VoicemailFile>)
    Organizer-->>Converter: OutputSummary

    Converter-->>Main: Success
    Main-->>User: Conversion complete! 12 voicemails converted
```

### 6.2 Backup Discovery Sequence

```mermaid
sequenceDiagram
    participant Manager as Backup Manager
    participant FileSystem as File System
    participant Plist as Plist Parser
    participant Validator

    Manager->>FileSystem: List directories in backup path
    FileSystem-->>Manager: [00008030-001E..., 00008120-002F...]

    loop For each directory
        Manager->>FileSystem: Check for Info.plist
        FileSystem-->>Manager: File exists

        Manager->>Plist: parse(Info.plist)
        Plist-->>Manager: DeviceInfo

        Manager->>Plist: parse(Manifest.plist)
        Plist-->>Manager: BackupMetadata

        Manager->>Validator: validate(backup)
        Validator-->>Manager: Valid/Invalid
    end

    Manager->>Manager: Build List<BackupInfo>
    Manager->>Manager: Sort by date (newest first)
```

### 6.3 Voicemail Extraction Sequence

```mermaid
sequenceDiagram
    participant Extractor
    participant Manifest as Manifest.db
    participant VoicemailDB as voicemail.db
    participant FileSystem as File System
    participant Temp as Temp Directory

    Extractor->>Manifest: SELECT fileID WHERE domain='Library-Voicemail' AND relativePath='voicemail.db'
    Manifest-->>Extractor: fileID: 3d0d7e5f...

    Extractor->>FileSystem: Copy 3d/3d0d7e5f... to temp
    FileSystem-->>Temp: voicemail.db

    Extractor->>VoicemailDB: SELECT * FROM voicemail WHERE trashed_date IS NULL
    VoicemailDB-->>Extractor: 12 rows of metadata

    Extractor->>Manifest: SELECT fileID WHERE relativePath LIKE 'voicemail/%.amr'
    Manifest-->>Extractor: 12 file hashes

    loop For each audio file
        Extractor->>FileSystem: Copy {hash[0:2]}/{hash} to temp
        FileSystem-->>Temp: original_1699123456.amr

        Extractor->>Extractor: Match with metadata by timestamp
    end

    Extractor->>Extractor: Build List<VoicemailFile>
```

### 6.4 Audio Conversion Sequence

```mermaid
sequenceDiagram
    participant Converter as Audio Converter
    participant Analyzer as Audio Analyzer
    participant FFmpeg
    participant Embedder as Metadata Embedder
    participant Output as Output File

    Converter->>Analyzer: analyze(input.amr)
    Analyzer->>FFmpeg: ffprobe -v quiet -print_format json input.amr
    FFmpeg-->>Analyzer: {codec: "amr_nb", duration: 45.234, ...}
    Analyzer-->>Converter: AudioInfo

    Converter->>Embedder: buildMetadata(VoicemailFile)
    Embedder-->>Converter: Map<String,String>

    Converter->>FFmpeg: ffmpeg -i input.amr -ar 44100 -ac 1 -metadata ...

    loop Progress updates
        FFmpeg-->>Converter: time=00:00:12.34 ...
        Converter->>Converter: Update progress (27%)
    end

    FFmpeg-->>Output: output.wav created

    Converter->>Converter: Verify output file
    Converter-->>Converter: Conversion successful
```

### 6.5 File Organization Sequence

```mermaid
sequenceDiagram
    participant Organizer as File Organizer
    participant NameGen as Filename Generator
    participant DirCreator as Directory Creator
    participant FileSystem as File System

    Organizer->>DirCreator: createOutputStructure(outputDir)
    DirCreator->>FileSystem: Create ./voicemail-wavs/2025-11-08/

    alt --keep-originals
        DirCreator->>FileSystem: Create ./voicemail-backup/2025-11-08/
    end

    loop For each VoicemailFile
        Organizer->>NameGen: generate(metadata)
        NameGen-->>Organizer: voicemail-2023-11-04T15-30-56-+1234567890

        Organizer->>FileSystem: Copy temp/converted.wav to voicemail-wavs/.../voicemail-....wav

        alt --include-metadata
            Organizer->>FileSystem: Write voicemail-wavs/.../voicemail-....json
        end

        alt --keep-originals
            Organizer->>FileSystem: Copy temp/original.amr to voicemail-backup/.../voicemail-....amr
        end
    end

    Organizer->>FileSystem: Delete temp directory
    Organizer-->>Organizer: Generate summary
```

---

## 7. Deployment View

Shows runtime environment and dependencies.

```mermaid
graph TB
    subgraph "User's Computer"
        subgraph "Runtime Environment"
            JVM[Java Virtual Machine<br/>Java 17+]
            JAR[voicemail-converter.jar]
        end

        subgraph "External Tools"
            FFmpegBin[FFmpeg Binary<br/>v4.0+]
            FFprobeBin[FFprobe Binary<br/>v4.0+]
        end

        subgraph "File System"
            BackupDir[iOS Backup Directory<br/>~/Library/.../MobileSync/Backup/]
            WavDir[WAV Output<br/>./voicemail-wavs/]
            BackupCopyDir[Backup Copy<br/>./voicemail-backup/]
            TempDir[Temp Directory<br/>/tmp/voicemail-converter-*]
            LogDir[Log Directory<br/>~/.voicemail-converter/logs/]
        end

        JVM --> JAR
        JAR --> FFmpegBin
        JAR --> FFprobeBin
        JAR --> BackupDir
        JAR --> WavDir
        JAR --> BackupCopyDir
        JAR --> TempDir
        JAR --> LogDir
    end

    style JVM fill:#4A90E2,stroke:#2E5C8A,color:#fff
    style JAR fill:#50C878,stroke:#2E7D4E,color:#fff
    style FFmpegBin fill:#FF6B6B,stroke:#CC5555,color:#fff
    style FFprobeBin fill:#FF6B6B,stroke:#CC5555,color:#fff
    style BackupDir fill:#FFB84D,stroke:#CC8800,color:#fff
    style WavDir fill:#A78BFA,stroke:#7C3AED,color:#fff
    style BackupCopyDir fill:#A78BFA,stroke:#7C3AED,color:#fff
```

### Runtime Requirements

| Component | Version | Purpose |
|-----------|---------|---------|
| Java | 17+ | Runtime environment |
| FFmpeg | 4.0+ | Audio conversion |
| ffprobe | 4.0+ | Audio analysis |
| SQLite JDBC | 3.40+ | Database access |

### File System Interactions

```
Read Operations:
  ✓ iOS Backup directory (read-only)
  ✓ Info.plist, Manifest.plist
  ✓ Manifest.db (SQLite)
  ✓ voicemail.db (SQLite)
  ✓ Hashed backup files

Write Operations:
  ✓ Temp directory (create, write, delete)
  ✓ Output directory (create, write)
  ✓ Log directory (create, append)

No Modifications:
  ✗ iOS Backup files (never modified)
```

---

## 8. Error Handling Flow

```mermaid
flowchart TB
    Start([User Executes Command])

    Start --> ParseArgs{Parse<br/>Arguments}
    ParseArgs -->|Invalid| ErrConfig[Configuration Error<br/>Exit Code 2]
    ParseArgs -->|Valid| CheckDeps{Check<br/>Dependencies}

    CheckDeps -->|FFmpeg Missing| ErrDeps[Dependency Error<br/>Exit Code 6]
    CheckDeps -->|OK| FindBackup{Find<br/>Backup}

    FindBackup -->|Not Found| ErrBackup[Backup Error<br/>Exit Code 3]
    FindBackup -->|Multiple| ErrMultiple[Multiple Backups<br/>Exit Code 2]
    FindBackup -->|Found| CheckEncrypt{Encrypted?}

    CheckEncrypt -->|Yes, No Password| ErrEncrypt[Encryption Error<br/>Exit Code 4]
    CheckEncrypt -->|Yes, Invalid Password| ErrEncrypt
    CheckEncrypt -->|No or Valid Password| Extract{Extract<br/>Voicemails}

    Extract -->|No Voicemails| ErrNoData[No Data Error<br/>Exit Code 5]
    Extract -->|Permission Denied| ErrPerm[Permission Error<br/>Exit Code 7]
    Extract -->|Success| Convert{Convert<br/>Audio}

    Convert -->|No Disk Space| ErrDisk[Disk Space Error<br/>Exit Code 8]
    Convert -->|Individual Failures| PartialSuccess[Partial Success<br/>Log Errors, Continue]
    Convert -->|Success| Organize{Organize<br/>Output}

    PartialSuccess --> Organize

    Organize -->|Permission Denied| ErrPerm
    Organize -->|Success| Complete([Success<br/>Exit Code 0])

    ErrConfig --> End([Exit])
    ErrDeps --> End
    ErrBackup --> End
    ErrMultiple --> End
    ErrEncrypt --> End
    ErrNoData --> End
    ErrPerm --> End
    ErrDisk --> End
    Complete --> End

    style Start fill:#50C878,stroke:#2E7D4E,color:#fff
    style Complete fill:#50C878,stroke:#2E7D4E,color:#fff
    style ErrConfig fill:#FF6B6B,stroke:#CC5555,color:#fff
    style ErrDeps fill:#FF6B6B,stroke:#CC5555,color:#fff
    style ErrBackup fill:#FF6B6B,stroke:#CC5555,color:#fff
    style ErrMultiple fill:#FF6B6B,stroke:#CC5555,color:#fff
    style ErrEncrypt fill:#FF6B6B,stroke:#CC5555,color:#fff
    style ErrNoData fill:#FF6B6B,stroke:#CC5555,color:#fff
    style ErrPerm fill:#FF6B6B,stroke:#CC5555,color:#fff
    style ErrDisk fill:#FF6B6B,stroke:#CC5555,color:#fff
    style PartialSuccess fill:#FFB84D,stroke:#CC8800,color:#fff
```

---

## 9. Module Dependency Graph

Shows compile-time dependencies between modules.

```mermaid
graph TB
    subgraph "Entry Point"
        Main[Main]
    end

    subgraph "Core Modules"
        CLI[CLI Module]
        Backup[Backup Module]
        Extract[Extraction Module]
        Meta[Metadata Module]
        Convert[Conversion Module]
        Output[Output Module]
    end

    subgraph "Utility Modules"
        Logger[Logger Module]
        Validator[Validator Module]
        Temp[Temp Manager]
    end

    subgraph "External Libraries"
        CommonsCLI[Apache Commons CLI]
        SQLite[SQLite JDBC]
        Plist[DD-Plist or XML]
        SLF4J[SLF4J]
    end

    Main --> CLI
    Main --> Logger

    CLI --> CommonsCLI
    CLI --> Backup

    Backup --> Extract
    Backup --> Plist
    Backup --> Validator

    Extract --> Meta
    Extract --> SQLite
    Extract --> Temp

    Meta --> Convert
    Meta --> Validator

    Convert --> Output

    Logger --> SLF4J
    Backup --> Logger
    Extract --> Logger
    Meta --> Logger
    Convert --> Logger
    Output --> Logger

    style Main fill:#4A90E2,stroke:#2E5C8A,color:#fff
    style CLI fill:#50C878,stroke:#2E7D4E,color:#fff
    style Backup fill:#50C878,stroke:#2E7D4E,color:#fff
    style Extract fill:#50C878,stroke:#2E7D4E,color:#fff
    style Meta fill:#50C878,stroke:#2E7D4E,color:#fff
    style Convert fill:#50C878,stroke:#2E7D4E,color:#fff
    style Output fill:#50C878,stroke:#2E7D4E,color:#fff
    style CommonsCLI fill:#FFB84D,stroke:#CC8800,color:#000
    style SQLite fill:#FFB84D,stroke:#CC8800,color:#000
    style Plist fill:#FFB84D,stroke:#CC8800,color:#000
    style SLF4J fill:#FFB84D,stroke:#CC8800,color:#000
```

---

## 10. Package Structure

Detailed view of Java package organization.

```
com.voicemail
│
├── Main.java                           # Entry point
│
├── cli/                                # CLI Module
│   ├── CLIParser.java                  # Argument parsing
│   ├── Arguments.java                  # Config object
│   └── HelpFormatter.java              # Usage display
│
├── backup/                             # Backup Module
│   ├── BackupDiscovery.java            # Locate backups
│   ├── BackupInfo.java                 # Backup metadata
│   ├── BackupValidator.java            # Validation logic
│   ├── PlistParser.java                # Parse .plist files
│   └── EncryptionHandler.java          # Decrypt backups
│
├── extractor/                          # Extraction Module
│   ├── VoicemailExtractor.java         # Main orchestrator
│   ├── ManifestDbReader.java           # Read Manifest.db
│   ├── VoicemailDbReader.java          # Read voicemail.db
│   ├── FileExtractor.java              # Extract files
│   └── FileMatcher.java                # Match audio+metadata
│
├── metadata/                           # Metadata Module
│   ├── MetadataParser.java             # Parse voicemail.db
│   ├── VoicemailMetadata.java          # Metadata object
│   ├── PhoneNumberFormatter.java       # Normalize phones
│   ├── MetadataEmbedder.java           # Embed in WAV
│   └── JSONExporter.java               # Export to JSON
│
├── converter/                          # Conversion Module
│   ├── AudioConverter.java             # Main orchestrator
│   ├── FFmpegWrapper.java              # FFmpeg integration
│   ├── FFmpegDetector.java             # Check installation
│   ├── AudioAnalyzer.java              # FFprobe wrapper
│   ├── AudioFormat.java                # Format enum
│   ├── ConversionResult.java           # Result object
│   └── ProgressTracker.java            # Track progress
│
├── output/                             # Output Module
│   ├── FileOrganizer.java              # Main orchestrator
│   ├── FilenameGenerator.java          # Generate names
│   ├── DirectoryCreator.java           # Create structure
│   └── OriginalFileKeeper.java         # Copy originals
│
├── util/                               # Utility Module
│   ├── Logger.java                     # Logging facade
│   ├── TempDirectoryManager.java       # Temp file mgmt
│   ├── ValidationUtil.java             # Validation helpers
│   └── FileSystemUtil.java             # FS operations
│
└── exception/                          # Exception Module
    ├── VoicemailConverterException.java
    ├── ConfigurationException.java
    ├── DependencyException.java
    ├── BackupException.java
    ├── EncryptionException.java
    ├── PermissionException.java
    └── InsufficientStorageException.java
```

---

## Design Patterns Used

### 1. **Facade Pattern**
- `VoicemailConverter` provides simplified interface to complex subsystems
- Hides complexity of backup discovery, extraction, conversion

### 2. **Strategy Pattern**
- Different conversion strategies (WAV, MP3, FLAC in future)
- Different metadata embedding strategies (WAV chunks vs JSON)

### 3. **Builder Pattern**
- `Arguments` object built from CLI parameters
- Complex FFmpeg command construction

### 4. **Template Method Pattern**
- Base conversion flow with hooks for format-specific operations
- Error handling template with specific implementations

### 5. **Factory Pattern**
- Create appropriate metadata parser based on iOS version
- Create format-specific converters

### 6. **Observer Pattern**
- Progress updates during conversion
- Logging across all components

### 7. **Command Pattern**
- Encapsulate FFmpeg invocations as command objects
- Easier to test, retry, and log

---

## Performance Considerations

### 1. **Parallel Processing**
```
Sequential:  12 files × 2s = 24s
Parallel (4): 12 files / 4 × 2s = 6s

Strategy: ThreadPoolExecutor with 4 threads
```

### 2. **Memory Management**
```
- Stream large files instead of loading into memory
- Close database connections promptly
- Clean up temp files incrementally
```

### 3. **Disk I/O Optimization**
```
- Batch file operations where possible
- Use buffered streams
- Minimize temp file writes
```

---

## Security Considerations

### 1. **Backup Password Handling**
```
✓ Never log passwords
✓ Clear from memory after use
✓ Warn about command-line visibility
```

### 2. **Filesystem Security**
```
✓ Validate all paths (no traversal)
✓ Check permissions before operations
✓ Fail securely (don't expose internals)
```

### 3. **Input Validation**
```
✓ Sanitize all filenames
✓ Validate UDID format
✓ Prevent SQL injection (use prepared statements)
```

---

## Rendering These Diagrams

### GitHub / GitLab
These Mermaid diagrams will render automatically when viewing this file on GitHub or GitLab.

### VSCode
Install the "Markdown Preview Mermaid Support" extension.

### Command Line
```bash
# Install mermaid-cli
npm install -g @mermaid-js/mermaid-cli

# Render to PNG
mmdc -i ARCHITECTURE.md -o architecture.png

# Render to SVG
mmdc -i ARCHITECTURE.md -o architecture.svg
```

### Online Tools
- [Mermaid Live Editor](https://mermaid.live/)
- Copy/paste diagrams for interactive editing

---

**End of Architecture Documentation**
