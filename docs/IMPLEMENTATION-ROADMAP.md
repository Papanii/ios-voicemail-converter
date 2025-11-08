# iOS Voicemail Converter - Implementation Roadmap

**Version:** 1.0.0
**Date:** 2025-11-08
**Status:** Specification Phase Complete - Ready for Implementation

---

## Overview

This document provides the overall implementation roadmap for the iOS Voicemail Converter project. Each module has a detailed `CLAUDE.md` implementation guide in its respective package directory.

---

## Module Dependency Graph

```
Exception (base)
    ↓
Util (foundational)
    ↓
CLI ← Exception
    ↓
Backup ← Exception, Util
    ↓
Extractor ← Backup, Exception, Util
    ↓
Metadata ← Extractor, Exception, Util
    ↓
Converter ← Metadata, Exception, Util
    ↓
Output ← Converter, Metadata, Exception, Util
    ↓
Main (orchestrator) ← All modules
```

---

## Implementation Order

### Phase 1: Foundation (Week 1)
**Goal:** Build core infrastructure

| # | Module | Package | Status | Guide Location | Est. Time |
|---|--------|---------|--------|----------------|-----------|
| 1 | Exception | `com.voicemail.exception` | ✅ Spec Complete | `exception/CLAUDE.md` | 2 hours |
| 2 | Util | `com.voicemail.util` | ✅ Spec Complete | `util/CLAUDE.md` | 3 hours |
| 3 | CLI | `com.voicemail.cli` | ✅ Spec Complete | `cli/CLAUDE.md` | 4 hours |

**Deliverables:**
- Exception hierarchy (8 classes)
- Utility classes (6 classes)
- CLI parser with all arguments
- ~1,800 lines of code
- ~800 lines of tests

**Testing:**
```bash
mvn test -Dtest=*Exception*Test
mvn test -Dtest=*Util*Test
mvn test -Dtest=*CLI*Test
```

---

### Phase 2: Backup Discovery (Week 1-2)
**Goal:** Locate and validate iOS backups

| # | Module | Package | Status | Guide Location | Est. Time |
|---|--------|---------|--------|----------------|-----------|
| 4 | Backup | `com.voicemail.backup` | ✅ Spec Complete | `backup/CLAUDE.md` | 4 hours |

**Deliverables:**
- Backup discovery and enumeration
- Plist parsing (Info.plist, Manifest.plist)
- Backup validation logic
- Encryption detection
- ~800 lines of code
- ~300 lines of tests

**Testing:**
```bash
mvn test -Dtest=*Backup*Test
# Manual test with real iOS backup
java -cp target/voicemail-converter.jar com.voicemail.Main --help
```

---

### Phase 3: Voicemail Extraction (Week 2)
**Goal:** Extract voicemail files from backups

| # | Module | Package | Status | Guide Location | Est. Time |
|---|--------|---------|--------|----------------|-----------|
| 5 | Extractor | `com.voicemail.extractor` | ✅ Spec Complete | `extractor/CLAUDE.md` | 6 hours |

**Deliverables:**
- Manifest.db reader (SQLite)
- Voicemail.db parser
- File hash computation
- Audio file extraction
- File matching logic
- ~1,200 lines of code
- ~400 lines of tests

**Key Classes:**
- `ManifestDbReader`
- `VoicemailDbReader`
- `FileExtractor`
- `FileMatcher`
- `VoicemailFile` (data class)

---

### Phase 4: Metadata Processing (Week 2-3)
**Goal:** Parse and preserve voicemail metadata

| # | Module | Package | Status | Guide Location | Est. Time |
|---|--------|---------|--------|----------------|-----------|
| 6 | Metadata | `com.voicemail.metadata` | ✅ Spec Complete | `metadata/CLAUDE.md` | 5 hours |

**Deliverables:**
- Metadata parser (voicemail.db)
- Phone number formatting
- Metadata embedding (WAV)
- JSON export
- ~900 lines of code
- ~350 lines of tests

**Key Classes:**
- `MetadataParser`
- `VoicemailMetadata` (data class)
- `PhoneNumberFormatter`
- `MetadataEmbedder`
- `JSONExporter`

---

### Phase 5: Audio Conversion (Week 3)
**Goal:** Convert AMR to WAV using FFmpeg

| # | Module | Package | Status | Guide Location | Est. Time |
|---|--------|---------|--------|----------------|-----------|
| 7 | Converter | `com.voicemail.converter` | ✅ Spec Complete | `converter/CLAUDE.md` | 6 hours |

**Deliverables:**
- FFmpeg detection
- FFprobe integration
- Audio conversion logic
- Progress tracking
- Error handling
- ~1,000 lines of code
- ~350 lines of tests

**Key Classes:**
- `FFmpegWrapper`
- `FFmpegDetector`
- `AudioAnalyzer` (FFprobe)
- `AudioConverter`
- `ConversionResult`
- `ProgressTracker`

---

### Phase 6: Output Organization (Week 3-4)
**Goal:** Organize and save converted files

| # | Module | Package | Status | Guide Location | Est. Time |
|---|--------|---------|--------|----------------|-----------|
| 8 | Output | `com.voicemail.output` | ✅ Spec Complete | `output/CLAUDE.md` | 4 hours |

**Deliverables:**
- Filename generation
- Directory structure creation
- File organization
- Original file preservation
- ~700 lines of code
- ~300 lines of tests

**Key Classes:**
- `FilenameGenerator`
- `DirectoryCreator`
- `FileOrganizer`
- `OriginalFileKeeper`

---

### Phase 7: Integration & Testing (Week 4)
**Goal:** Wire everything together and test end-to-end

| # | Task | Est. Time |
|---|------|-----------|
| 1 | Implement `Main.java` orchestrator | 2 hours |
| 2 | Wire all modules together | 2 hours |
| 3 | End-to-end testing | 4 hours |
| 4 | Bug fixes and refinements | 4 hours |
| 5 | Documentation updates | 2 hours |

**Testing Checklist:**
- [ ] Basic conversion (no flags)
- [ ] With --keep-originals
- [ ] With --include-metadata
- [ ] With --verbose
- [ ] Encrypted backup with password
- [ ] Multiple backups (device selection)
- [ ] Error scenarios (no backup, no FFmpeg, etc.)
- [ ] Large backup (100+ voicemails)
- [ ] Cross-platform (macOS, Windows, Linux)

---

## Total Estimates

### Code Volume
| Category | Lines of Code | Test Lines |
|----------|---------------|------------|
| Exception | 250 | 100 |
| Util | 600 | 350 |
| CLI | 900 | 350 |
| Backup | 800 | 300 |
| Extractor | 1,200 | 400 |
| Metadata | 900 | 350 |
| Converter | 1,000 | 350 |
| Output | 700 | 300 |
| Main | 200 | 100 |
| **Total** | **~6,550** | **~2,600** |

### Time Estimates
| Phase | Hours |
|-------|-------|
| Phase 1: Foundation | 9 |
| Phase 2: Backup | 4 |
| Phase 3: Extractor | 6 |
| Phase 4: Metadata | 5 |
| Phase 5: Converter | 6 |
| Phase 6: Output | 4 |
| Phase 7: Integration | 14 |
| **Total** | **~48 hours** (~6 days @ 8 hours/day) |

---

## Implementation Guidelines

### Code Quality Standards
1. **Follow Java conventions** - camelCase, proper naming
2. **Write tests first** - TDD approach where possible
3. **Document public APIs** - Javadoc for all public methods
4. **Handle errors gracefully** - Use custom exceptions
5. **Log appropriately** - Use SLF4J levels correctly
6. **Keep it simple** - Avoid over-engineering

### Git Workflow
```bash
# Create feature branch
git checkout -b feature/exception-module

# Implement module
# ... code ...

# Test
mvn test

# Commit
git add .
git commit -m "Implement Exception module with all custom exceptions"

# Repeat for each module
```

### Build & Test Commands
```bash
# Clean build
mvn clean install

# Run specific test class
mvn test -Dtest=CLIParserTest

# Run all tests
mvn test

# Package JAR
mvn package

# Run application
java -jar target/voicemail-converter.jar --help
```

---

## Progress Tracking

### Completed ✅
- [x] Project structure setup
- [x] Maven POM configuration
- [x] CLI specification document
- [x] Architecture diagrams
- [x] Exception module spec (CLAUDE.md)
- [x] Util module spec (CLAUDE.md)
- [x] CLI module spec (CLAUDE.md)
- [x] Backup module spec (CLAUDE.md)
- [x] Extractor module spec (CLAUDE.md)
- [x] Metadata module spec (CLAUDE.md)
- [x] Converter module spec (CLAUDE.md)
- [x] Output module spec (CLAUDE.md)

### In Progress 🔄
- None - All specifications complete!

### Not Started 📋
- [ ] Exception module implementation
- [ ] Util module implementation
- [ ] CLI module implementation
- [ ] Backup module implementation
- [ ] Extractor module implementation
- [ ] Metadata module implementation
- [ ] Converter module implementation
- [ ] Output module implementation
- [ ] Main orchestrator implementation
- [ ] End-to-end testing
- [ ] User documentation

---

## Milestones

### Milestone 1: Foundation Complete
**Target:** End of Week 1
**Deliverable:** CLI working, can parse arguments and show help
**Test:** `java -jar voicemail-converter.jar --help`

### Milestone 2: Backup Discovery Works
**Target:** Middle of Week 2
**Deliverable:** Can discover and validate backups
**Test:** `java -jar voicemail-converter.jar` (finds backup)

### Milestone 3: Extraction Works
**Target:** End of Week 2
**Deliverable:** Can extract voicemail files from backup
**Test:** Extract files to temp directory

### Milestone 4: Conversion Works
**Target:** End of Week 3
**Deliverable:** Can convert AMR to WAV
**Test:** One file converts successfully

### Milestone 5: MVP Complete
**Target:** End of Week 4
**Deliverable:** Full pipeline works end-to-end
**Test:** Complete conversion of all voicemails

---

## Risk Mitigation

### Technical Risks

| Risk | Impact | Mitigation |
|------|--------|------------|
| FFmpeg not available | High | Clear error message, installation guide |
| Backup encrypted without password | High | Detect early, prompt for password |
| Corrupted backup files | Medium | Validation, graceful degradation |
| Unsupported iOS version | Low | Version check, clear error |
| Out of disk space | Medium | Pre-flight space check |

### Development Risks

| Risk | Impact | Mitigation |
|------|--------|------------|
| Underestimated complexity | Medium | Phased approach, can ship MVP early |
| Testing on single platform only | High | Test on macOS, Windows, Linux |
| Missing edge cases | Medium | Comprehensive test suite |
| Poor error messages | Low | User testing, iterate |

---

## Next Steps

1. ✅ ~~**Complete specification phase**~~ - All CLAUDE.md guides complete!
2. **Review all specs** - Ensure consistency and completeness
3. **Begin Phase 1 implementation** - Start with Exception module
4. **Set up CI/CD** - Automated testing on each commit
5. **Create test fixtures** - Sample backup files for testing

---

## Resources

### Documentation
- [SPECIFICATIONS.md](../SPECIFICATIONS.md) - Complete specifications
- [ARCHITECTURE.md](../ARCHITECTURE.md) - Architecture diagrams
- [CLI-SPECIFICATION.md](./CLI-SPECIFICATION.md) - CLI details
- Module CLAUDE.md files - Implementation guides

### External References
- [iOS Backup Format](https://theiphonewiki.com/wiki/ITunes_Backup)
- [FFmpeg Documentation](https://ffmpeg.org/documentation.html)
- [AMR Audio Format](https://en.wikipedia.org/wiki/Adaptive_Multi-Rate_audio_codec)

---

**End of Implementation Roadmap**
