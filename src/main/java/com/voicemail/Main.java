package com.voicemail;

import com.voicemail.cli.Arguments;
import com.voicemail.cli.CLIParser;
import com.voicemail.exception.ConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main entry point for iOS Voicemail Converter application
 */
public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        try {
            // Parse CLI arguments
            CLIParser parser = new CLIParser();
            Arguments arguments = parser.parse(args);

            // If null, help or version was displayed, exit cleanly
            if (arguments == null) {
                System.exit(0);
            }

            log.info("Starting iOS Voicemail Converter");
            log.debug("Arguments: {}", arguments);

            // Run the conversion workflow
            VoicemailConverter converter = new VoicemailConverter(arguments);
            int exitCode = converter.run();

            log.info("Application completed with exit code: {}", exitCode);
            System.exit(exitCode);

        } catch (ConfigurationException e) {
            // CLI error - display to user
            System.err.println("Error: " + e.getMessage());
            if (e.hasSuggestion()) {
                System.err.println();
                System.err.println("Suggestion:");
                System.err.println("  " + e.getSuggestion());
            }
            log.error("Configuration error: {}", e.getMessage());
            System.exit(e.getExitCode());

        } catch (Exception e) {
            // Unexpected error
            log.error("Unexpected error", e);
            System.err.println("Unexpected error: " + e.getMessage());
            System.err.println("Please report this issue with the log file");
            System.exit(1);
        }
    }
}
