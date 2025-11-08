package com.voicemail.exception;

/**
 * Exception thrown when required dependencies are missing.
 * Exit code: 6
 */
public class DependencyException extends VoicemailConverterException {
    private final String dependency;

    public DependencyException(String dependency, String message) {
        super(message, 6, buildSuggestion(dependency));
        this.dependency = dependency;
    }

    public DependencyException(String dependency, String message, Throwable cause) {
        super(message, 6, buildSuggestion(dependency), cause);
        this.dependency = dependency;
    }

    public String getDependency() {
        return dependency;
    }

    private static String buildSuggestion(String dependency) {
        switch (dependency.toLowerCase()) {
            case "ffmpeg":
                return "Install FFmpeg:\n" +
                       "  macOS:    brew install ffmpeg\n" +
                       "  Ubuntu:   sudo apt install ffmpeg\n" +
                       "  Windows:  Download from https://ffmpeg.org/download.html";
            case "ffprobe":
                return "FFprobe is included with FFmpeg. Install FFmpeg.";
            default:
                return "Install " + dependency + " and ensure it's in your PATH";
        }
    }
}
