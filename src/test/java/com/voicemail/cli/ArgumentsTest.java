package com.voicemail.cli;

import org.junit.jupiter.api.Test;

import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

class ArgumentsTest {

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
        assertFalse(args.isKeepOriginals());
        assertFalse(args.isIncludeMetadata());
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

        assertEquals(Paths.get("/backup"), args.getBackupDir());
        assertEquals(Paths.get("/output"), args.getOutputDir());
        assertTrue(args.getDeviceId().isPresent());
        assertEquals("12345", args.getDeviceId().get());
        assertTrue(args.getBackupPassword().isPresent());
        assertEquals("secret", args.getBackupPassword().get());
        assertEquals("wav", args.getFormat());
        assertTrue(args.isKeepOriginals());
        assertTrue(args.isIncludeMetadata());
        assertTrue(args.isVerbose());
        assertTrue(args.getLogFile().isPresent());
        assertEquals(Paths.get("/log.txt"), args.getLogFile().get());
    }

    @Test
    void testBuilderMissingBackupDir() {
        Arguments.Builder builder = new Arguments.Builder()
            .outputDir(Paths.get("/output"));

        IllegalStateException ex = assertThrows(
            IllegalStateException.class,
            () -> builder.build()
        );
        assertTrue(ex.getMessage().contains("backupDir"));
    }

    @Test
    void testBuilderMissingOutputDir() {
        Arguments.Builder builder = new Arguments.Builder()
            .backupDir(Paths.get("/backup"));

        IllegalStateException ex = assertThrows(
            IllegalStateException.class,
            () -> builder.build()
        );
        assertTrue(ex.getMessage().contains("outputDir"));
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

    @Test
    void testToStringMasksPassword() {
        Arguments args = new Arguments.Builder()
            .backupDir(Paths.get("/backup"))
            .outputDir(Paths.get("/output"))
            .deviceId("12345")
            .backupPassword("secret")
            .build();

        String toString = args.toString();

        // Should contain "***" for password, not actual value
        assertTrue(toString.contains("backupPassword=***"));
        assertFalse(toString.contains("secret"));

        // Device ID should also be masked
        assertTrue(toString.contains("deviceId=***"));
        assertFalse(toString.contains("12345"));
    }

    @Test
    void testToStringWithNullOptionals() {
        Arguments args = new Arguments.Builder()
            .backupDir(Paths.get("/backup"))
            .outputDir(Paths.get("/output"))
            .build();

        String toString = args.toString();

        assertTrue(toString.contains("deviceId=null"));
        assertTrue(toString.contains("backupPassword=null"));
    }

    @Test
    void testBuilderIsChainable() {
        // Test that builder methods return this
        Arguments args = new Arguments.Builder()
            .backupDir(Paths.get("/backup"))
            .outputDir(Paths.get("/output"))
            .deviceId("12345")
            .format("wav")
            .keepOriginals(true)
            .verbose(true)
            .build();

        assertNotNull(args);
    }

    @Test
    void testDefaultValues() {
        Arguments args = new Arguments.Builder()
            .backupDir(Paths.get("/backup"))
            .outputDir(Paths.get("/output"))
            .build();

        assertEquals("wav", args.getFormat());
        assertFalse(args.isKeepOriginals());
        assertFalse(args.isIncludeMetadata());
        assertFalse(args.isVerbose());
    }
}
