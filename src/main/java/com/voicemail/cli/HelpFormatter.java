package com.voicemail.cli;

/**
 * Formats help and version information for display to user
 */
public class HelpFormatter {
    private static final String VERSION = "1.0.0";
    private static final String APP_NAME = "iOS Voicemail Converter";

    /**
     * Get the full help text
     * @return Formatted help text
     */
    public String formatHelp() {
        StringBuilder sb = new StringBuilder();

        sb.append(APP_NAME).append(" - Extract and convert iOS voicemails from backups\n\n");
        sb.append(formatUsage()).append("\n\n");
        sb.append(formatOptions()).append("\n\n");
        sb.append(formatExamples()).append("\n\n");
        sb.append(formatRequirements()).append("\n");

        return sb.toString();
    }

    /**
     * Get the usage line
     * @return Formatted usage text
     */
    public String formatUsage() {
        return "USAGE:\n    java -jar voicemail-converter.jar [OPTIONS]";
    }

    /**
     * Get the options section
     * @return Formatted options text
     */
    public String formatOptions() {
        StringBuilder sb = new StringBuilder("OPTIONS:");

        addOption(sb, "-b, --backup-dir <path>", "iOS backup directory (default: auto-detect)");
        addOption(sb, "-o, --output-dir <path>", "Output directory (default: ./voicemail-wavs/)");
        addOption(sb, "-d, --device-id <udid>", "Target specific device UDID");
        addOption(sb, "-p, --backup-password <pass>", "Password for encrypted backup");
        addOption(sb, "-f, --format <format>", "Output format: wav (default: wav)");
        addOption(sb, "    --keep-originals", "Copy original AMR files to ./voicemail-backup/");
        addOption(sb, "    --include-metadata", "Export metadata as JSON files");
        addOption(sb, "-v, --verbose", "Enable detailed logging");
        addOption(sb, "-l, --log-file <path>", "Write logs to file");
        addOption(sb, "-h, --help", "Show this help message");
        addOption(sb, "    --version", "Show version information");

        return sb.toString();
    }

    /**
     * Get the examples section
     * @return Formatted examples text
     */
    public String formatExamples() {
        StringBuilder sb = new StringBuilder("EXAMPLES:\n");

        sb.append("    # Basic usage (auto-detect backup, output to ./voicemail-wavs/)\n");
        sb.append("    java -jar voicemail-converter.jar\n\n");

        sb.append("    # With verbose output and original files\n");
        sb.append("    java -jar voicemail-converter.jar --verbose --keep-originals\n\n");

        sb.append("    # Custom directories\n");
        sb.append("    java -jar voicemail-converter.jar -b ~/Backups -o ~/Desktop/VM\n\n");

        sb.append("    # Encrypted backup with metadata export\n");
        sb.append("    java -jar voicemail-converter.jar -p mypassword --include-metadata\n\n");

        sb.append("    # Specific device\n");
        sb.append("    java -jar voicemail-converter.jar -d 00008030001E4D8A3602802E\n");

        return sb.toString();
    }

    /**
     * Get the requirements section
     * @return Formatted requirements text
     */
    public String formatRequirements() {
        StringBuilder sb = new StringBuilder("REQUIREMENTS:\n");
        sb.append("    - Java 17 or higher\n");
        sb.append("    - FFmpeg installed and in PATH\n");
        sb.append("    - Existing iOS backup (created via iTunes/Finder)\n\n");
        sb.append("For more information: https://github.com/yourusername/voicemail-converter");
        return sb.toString();
    }

    /**
     * Get version information
     * @return Formatted version text
     */
    public String formatVersion() {
        StringBuilder sb = new StringBuilder();

        sb.append(APP_NAME).append(" v").append(VERSION).append("\n\n");

        sb.append("Java Version:  ").append(System.getProperty("java.version")).append("\n");
        sb.append("Java VM:       ").append(System.getProperty("java.vm.name")).append("\n");
        sb.append("OS:            ").append(System.getProperty("os.name"))
          .append(" ").append(System.getProperty("os.version")).append("\n");
        sb.append("Architecture:  ").append(System.getProperty("os.arch")).append("\n\n");

        sb.append("Copyright (c) 2025 ").append(APP_NAME).append(" Contributors\n");
        sb.append("Licensed under MIT License\n\n");
        sb.append("For help: java -jar voicemail-converter.jar --help\n");

        return sb.toString();
    }

    /**
     * Helper to add an option line
     */
    private void addOption(StringBuilder sb, String option, String description) {
        sb.append("\n    ");
        sb.append(String.format("%-35s", option));
        sb.append(description);
    }
}
