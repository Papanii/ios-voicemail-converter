# Changelog - iOS Voicemail Converter

## Version 1.0.0 - Specification Updates (2025-11-08)

### Changed

#### Output Directory Structure
- **Default output directory changed** from `~/Voicemails/` to `./voicemail-wavs/`
  - Now defaults to current working directory instead of home directory
  - User can still specify custom location with `--output-dir`
  - Makes it easier to control where files are placed

#### Original File Storage
- **Separate backup folder** for original AMR files
  - Previously: Originals stored in `originals/` subdirectory within output folder
  - Now: Originals stored in `./voicemail-backup/` at the same level as `voicemail-wavs/`
  - Both folders maintain the same date-based subdirectory structure (e.g., `2025-11-08/`)
  - Only created when `--keep-originals` flag is used

#### CLI Arguments
- **Updated `--output-dir` description**
  - New default: `./voicemail-wavs/`
  - Clarified it's configurable with sensible default

- **Updated `--keep-originals` description**
  - Now explicitly states originals go to `./voicemail-backup/`

- **Updated `--backup-dir` description**
  - Clarified as "optional override" since auto-detection is primary method

#### Directory Creation Logic
Updated specification to reflect:
1. Create `voicemail-wavs/` directory (or custom `--output-dir`)
2. Create date subdirectory: `voicemail-wavs/YYYY-MM-DD/`
3. If `--keep-originals`: Create `voicemail-backup/` and `voicemail-backup/YYYY-MM-DD/`
4. Both directories created at the same level (not nested)

### Examples

#### Before (Old Structure)
```
~/Voicemails/
└── 2025-11-08/
    ├── voicemail-2023-11-04T15-30-56-+1234567890.wav
    └── originals/
        └── voicemail-2023-11-04T15-30-56-+1234567890.amr
```

#### After (New Structure)
```
./voicemail-wavs/
└── 2025-11-08/
    └── voicemail-2023-11-04T15-30-56-+1234567890.wav

./voicemail-backup/
└── 2025-11-08/
    └── voicemail-2023-11-04T15-30-56-+1234567890.amr
```

### Architecture Updates

#### Deployment View Diagram
- Split single "Output Directory" into two separate nodes:
  - `WAV Output: ./voicemail-wavs/`
  - `Backup Copy: ./voicemail-backup/`
- Both shown as separate filesystem locations in deployment diagram

#### Sequence Diagrams
- Updated File Organization sequence to show:
  - Directory creation happens conditionally based on flags
  - `voicemail-backup/` created at same level as `voicemail-wavs/`
  - Originals copied to separate location, not subdirectory

### Files Modified
- ✅ `SPECIFICATIONS.md` - Updated CLI arguments, examples, and output organization section
- ✅ `ARCHITECTURE.md` - Updated deployment view and file organization sequence diagram
- ✅ `CHANGELOG.md` - Created this file to track changes

### Rationale
These changes provide:
1. **Better organization** - Separate folders for different file types
2. **Clearer intent** - `voicemail-wavs/` vs `voicemail-backup/` names are self-documenting
3. **More control** - Current directory default gives users more control over output location
4. **Consistency** - Both folders use the same date-based structure
5. **Flexibility** - Easier to process, backup, or share each folder independently

### Backward Compatibility
This is a specification change only (no code exists yet), so there are no backward compatibility concerns. When implementing:
- Consider adding a note in documentation about the folder structure
- Ensure clear error messages if directories can't be created
- Validate write permissions for both directories before starting

---

## Future Considerations

Potential enhancements based on new structure:
1. Add `--backup-output-dir` flag to specify custom location for `voicemail-backup/` separately
2. Add option to skip date subdirectories for flat structure
3. Add symlink creation between related WAV and AMR files
4. Add option to organize by device name instead of date
5. Create index files in root of each directory for easy lookup

---

**End of Changelog**
