package com.voicemail.metadata;

import com.voicemail.extractor.VoicemailFile;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MetadataTest {

    @Test
    void testPhoneNormalization() {
        assertEquals("+12345678900", PhoneNumberFormatter.normalizePhoneNumber("(234) 567-8900"));
        assertEquals("+12345678900", PhoneNumberFormatter.normalizePhoneNumber("+1-234-567-8900"));
        assertEquals("+12345678900", PhoneNumberFormatter.normalizePhoneNumber("2345678900"));
        assertEquals("Unknown", PhoneNumberFormatter.normalizePhoneNumber("Unknown"));
        assertEquals("Unknown", PhoneNumberFormatter.normalizePhoneNumber(""));
        assertEquals("Unknown", PhoneNumberFormatter.normalizePhoneNumber(null));
    }

    @Test
    void testPhoneFormatting() {
        assertEquals("+1-234-567-8900", PhoneNumberFormatter.formatPhoneNumber("+12345678900"));
        assertEquals("Unknown", PhoneNumberFormatter.formatPhoneNumber("Unknown"));
        assertEquals("Unknown", PhoneNumberFormatter.formatPhoneNumber(""));
        assertEquals("Unknown", PhoneNumberFormatter.formatPhoneNumber(null));
    }

    @Test
    void testFilenameFormatting() {
        assertEquals("+12345678900", PhoneNumberFormatter.formatForFilename("(234) 567-8900"));
        assertEquals("Unknown", PhoneNumberFormatter.formatForFilename("Unknown"));
        assertEquals("Unknown", PhoneNumberFormatter.formatForFilename(""));
    }

    @Test
    void testCallerDisplayName() {
        assertEquals("+1-234-567-8900", PhoneNumberFormatter.getCallerDisplayName("+12345678900"));
        assertEquals("Unknown", PhoneNumberFormatter.getCallerDisplayName("Unknown"));
        assertEquals("Unknown", PhoneNumberFormatter.getCallerDisplayName(null));
    }

    @Test
    void testPhoneValidation() {
        assertTrue(PhoneNumberFormatter.isValidPhoneNumber("+12345678900"));
        assertTrue(PhoneNumberFormatter.isValidPhoneNumber("2345678900"));
        assertFalse(PhoneNumberFormatter.isValidPhoneNumber("Unknown"));
        assertFalse(PhoneNumberFormatter.isValidPhoneNumber(""));
        assertFalse(PhoneNumberFormatter.isValidPhoneNumber(null));
    }

    @Test
    void testMetadataEmbedder() {
        VoicemailFile.VoicemailMetadata metadata = new VoicemailFile.VoicemailMetadata(
            1, 100, Instant.now(), "+12345678900", "+12345678900",
            45, null, null, 0
        );

        VoicemailFile file = new VoicemailFile.Builder()
            .fileId("abc123")
            .relativePath("voicemail/test.amr")
            .backupFilePath(java.nio.file.Paths.get("/backup/ab/abc123"))
            .metadata(metadata)
            .build();

        MetadataEmbedder embedder = new MetadataEmbedder();
        Map<String, String> wavMetadata = embedder.buildMetadataMap(file, "Test iPhone");

        assertNotNull(wavMetadata);
        assertTrue(wavMetadata.containsKey("title"));
        assertTrue(wavMetadata.containsKey("artist"));
        assertTrue(wavMetadata.containsKey("comment"));
        assertTrue(wavMetadata.containsKey("date"));
        assertTrue(wavMetadata.containsKey("encoded_by"));
    }

    @Test
    void testMetadataEmbedderNoMetadata() {
        VoicemailFile file = new VoicemailFile.Builder()
            .fileId("abc123")
            .relativePath("voicemail/test.amr")
            .backupFilePath(java.nio.file.Paths.get("/backup/ab/abc123"))
            .build();

        MetadataEmbedder embedder = new MetadataEmbedder();
        Map<String, String> wavMetadata = embedder.buildMetadataMap(file, "Test iPhone");

        assertNotNull(wavMetadata);
        assertTrue(wavMetadata.isEmpty());
    }

    @Test
    void testMetadataProcessor() {
        VoicemailFile.VoicemailMetadata metadata = new VoicemailFile.VoicemailMetadata(
            1, 100, Instant.now(), "+12345678900", "+12345678900",
            45, null, null, 0
        );

        VoicemailFile file = new VoicemailFile.Builder()
            .fileId("abc123")
            .relativePath("voicemail/test.amr")
            .backupFilePath(java.nio.file.Paths.get("/backup/ab/abc123"))
            .metadata(metadata)
            .build();

        MetadataProcessor processor = new MetadataProcessor();
        MetadataProcessor.ProcessedMetadata processed = processor.processMetadata(
            file,
            "Test iPhone",
            "17.5",
            Instant.now()
        );

        assertNotNull(processed);
        assertEquals(file, processed.getVoicemailFile());
        assertEquals("Test iPhone", processed.getDeviceName());
        assertEquals("17.5", processed.getIosVersion());
        assertNotNull(processed.getWavMetadata());
        assertFalse(processed.getWavMetadata().isEmpty());
    }

    @Test
    void testFFmpegArgsGeneration() {
        MetadataEmbedder embedder = new MetadataEmbedder();

        Map<String, String> metadata = Map.of(
            "title", "Test Title",
            "artist", "Test Artist"
        );

        String[] args = embedder.generateFFmpegMetadataArgs(metadata);

        assertNotNull(args);
        assertEquals(4, args.length);
        assertTrue(args[0].equals("-metadata"));
    }

    @Test
    void testFFmpegArgsGenerationEmpty() {
        MetadataEmbedder embedder = new MetadataEmbedder();
        String[] args = embedder.generateFFmpegMetadataArgs(Map.of());

        assertNotNull(args);
        assertEquals(0, args.length);
    }
}
