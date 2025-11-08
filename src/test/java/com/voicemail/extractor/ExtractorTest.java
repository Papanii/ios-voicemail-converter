package com.voicemail.extractor;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class ExtractorTest {

    @Test
    void testVoicemailFileBuilder() {
        VoicemailFile.VoicemailMetadata metadata = new VoicemailFile.VoicemailMetadata(
            1, 100, Instant.now(), "+1234567890", "+1234567890",
            45, null, null, 0
        );

        VoicemailFile file = new VoicemailFile.Builder()
            .fileId("abc123")
            .relativePath("voicemail/1699123456.amr")
            .backupFilePath(java.nio.file.Paths.get("/backup/ab/abc123"))
            .metadata(metadata)
            .build();

        assertNotNull(file);
        assertTrue(file.hasMetadata());
        assertEquals("1699123456.amr", file.getOriginalFilename());
        assertEquals(VoicemailFile.AudioFormat.AMR_NB, file.getFormat());
    }

    @Test
    void testAudioFormatDetection() {
        assertEquals(VoicemailFile.AudioFormat.AMR_NB,
            VoicemailFile.AudioFormat.fromExtension(".amr"));
        assertEquals(VoicemailFile.AudioFormat.AMR_WB,
            VoicemailFile.AudioFormat.fromExtension(".awb"));
        assertEquals(VoicemailFile.AudioFormat.AAC,
            VoicemailFile.AudioFormat.fromExtension(".m4a"));
        assertEquals(VoicemailFile.AudioFormat.UNKNOWN,
            VoicemailFile.AudioFormat.fromExtension(".unknown"));
    }

    @Test
    void testVoicemailMetadata() {
        VoicemailFile.VoicemailMetadata metadata = new VoicemailFile.VoicemailMetadata(
            1, 100, Instant.now(), "+1234567890", "+1234567890",
            45, null, null, 0x01  // Read flag
        );

        assertTrue(metadata.isRead());
        assertFalse(metadata.isSpam());
        assertFalse(metadata.wasTrashed());
    }

    @Test
    void testVoicemailMetadataSpam() {
        VoicemailFile.VoicemailMetadata metadata = new VoicemailFile.VoicemailMetadata(
            1, 100, Instant.now(), "+1234567890", "+1234567890",
            45, null, null, 0x04  // Spam flag
        );

        assertFalse(metadata.isRead());
        assertTrue(metadata.isSpam());
        assertFalse(metadata.wasTrashed());
    }

    @Test
    void testVoicemailMetadataTrashed() {
        Instant now = Instant.now();
        VoicemailFile.VoicemailMetadata metadata = new VoicemailFile.VoicemailMetadata(
            1, 100, now, "+1234567890", "+1234567890",
            45, null, now, 0x00
        );

        assertTrue(metadata.wasTrashed());
        assertNotNull(metadata.getTrashedDate());
    }

    @Test
    void testVoicemailFileWithoutMetadata() {
        VoicemailFile file = new VoicemailFile.Builder()
            .fileId("abc123")
            .relativePath("voicemail/1699123456.amr")
            .backupFilePath(java.nio.file.Paths.get("/backup/ab/abc123"))
            .build();

        assertNotNull(file);
        assertFalse(file.hasMetadata());
        assertNull(file.getMetadata());
    }

    @Test
    void testOriginalFilenameExtraction() {
        VoicemailFile file = new VoicemailFile.Builder()
            .fileId("abc123")
            .relativePath("voicemail/subfolder/test.amr")
            .backupFilePath(java.nio.file.Paths.get("/backup/ab/abc123"))
            .build();

        assertEquals("test.amr", file.getOriginalFilename());
    }

    @Test
    void testFileMatcher() {
        Instant timestamp = Instant.ofEpochSecond(1699123456L);
        VoicemailFile.VoicemailMetadata syntheticMetadata =
            FileMatcher.createSyntheticMetadata(timestamp);

        assertNotNull(syntheticMetadata);
        assertEquals(timestamp, syntheticMetadata.getReceivedDate());
        assertEquals("Unknown", syntheticMetadata.getCallerNumber());
        assertEquals(0, syntheticMetadata.getDurationSeconds());
    }
}
