package com.voicemail.cli;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HelpFormatterTest {
    private HelpFormatter formatter;

    @BeforeEach
    void setUp() {
        formatter = new HelpFormatter();
    }

    @Test
    void testFormatHelp() {
        String help = formatter.formatHelp();

        assertNotNull(help);
        assertFalse(help.isEmpty());
        assertTrue(help.contains("iOS Voicemail Converter"));
        assertTrue(help.contains("USAGE:"));
        assertTrue(help.contains("OPTIONS:"));
        assertTrue(help.contains("EXAMPLES:"));
        assertTrue(help.contains("REQUIREMENTS:"));
    }

    @Test
    void testFormatUsage() {
        String usage = formatter.formatUsage();

        assertNotNull(usage);
        assertTrue(usage.contains("USAGE:"));
        assertTrue(usage.contains("java -jar voicemail-converter.jar"));
    }

    @Test
    void testFormatOptions() {
        String options = formatter.formatOptions();

        assertNotNull(options);
        assertTrue(options.contains("OPTIONS:"));
        assertTrue(options.contains("--backup-dir"));
        assertTrue(options.contains("--output-dir"));
        assertTrue(options.contains("--device-id"));
        assertTrue(options.contains("--backup-password"));
        assertTrue(options.contains("--format"));
        assertTrue(options.contains("--keep-originals"));
        assertTrue(options.contains("--include-metadata"));
        assertTrue(options.contains("--verbose"));
        assertTrue(options.contains("--log-file"));
        assertTrue(options.contains("--help"));
        assertTrue(options.contains("--version"));
    }

    @Test
    void testFormatOptionsIncludesShortFlags() {
        String options = formatter.formatOptions();

        assertTrue(options.contains("-b,"));
        assertTrue(options.contains("-o,"));
        assertTrue(options.contains("-d,"));
        assertTrue(options.contains("-p,"));
        assertTrue(options.contains("-f,"));
        assertTrue(options.contains("-v,"));
        assertTrue(options.contains("-l,"));
        assertTrue(options.contains("-h,"));
    }

    @Test
    void testFormatExamples() {
        String examples = formatter.formatExamples();

        assertNotNull(examples);
        assertTrue(examples.contains("EXAMPLES:"));
        assertTrue(examples.contains("Basic usage"));
        assertTrue(examples.contains("verbose"));
        assertTrue(examples.contains("Custom directories"));
        assertTrue(examples.contains("Encrypted backup"));
        assertTrue(examples.contains("Specific device"));
    }

    @Test
    void testFormatRequirements() {
        String requirements = formatter.formatRequirements();

        assertNotNull(requirements);
        assertTrue(requirements.contains("REQUIREMENTS:"));
        assertTrue(requirements.contains("Java 17"));
        assertTrue(requirements.contains("FFmpeg"));
        assertTrue(requirements.contains("iOS backup"));
    }

    @Test
    void testFormatVersion() {
        String version = formatter.formatVersion();

        assertNotNull(version);
        assertTrue(version.contains("iOS Voicemail Converter"));
        assertTrue(version.contains("v1.0.0"));
        assertTrue(version.contains("Java Version:"));
        assertTrue(version.contains("Java VM:"));
        assertTrue(version.contains("OS:"));
        assertTrue(version.contains("Architecture:"));
        assertTrue(version.contains("MIT License"));
    }

    @Test
    void testFormatVersionIncludesSystemInfo() {
        String version = formatter.formatVersion();

        // Should include actual system properties
        String javaVersion = System.getProperty("java.version");
        String osName = System.getProperty("os.name");

        assertTrue(version.contains(javaVersion));
        assertTrue(version.contains(osName));
    }

    @Test
    void testHelpContainsAllSections() {
        String help = formatter.formatHelp();

        // Verify all sections are present in order
        int usagePos = help.indexOf("USAGE:");
        int optionsPos = help.indexOf("OPTIONS:");
        int examplesPos = help.indexOf("EXAMPLES:");
        int requirementsPos = help.indexOf("REQUIREMENTS:");

        assertTrue(usagePos > 0);
        assertTrue(optionsPos > usagePos);
        assertTrue(examplesPos > optionsPos);
        assertTrue(requirementsPos > examplesPos);
    }
}
