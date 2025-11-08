package com.voicemail.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Manages temporary directory for voicemail processing
 */
public class TempDirectoryManager {
    private static final Logger log = LoggerFactory.getLogger(TempDirectoryManager.class);
    private static final String PREFIX = "voicemail-converter-";
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private Path tempDirectory;

    /**
     * Create and return temp directory
     */
    public Path createTempDirectory() throws IOException {
        if (tempDirectory != null && Files.exists(tempDirectory)) {
            log.debug("Temp directory already exists: {}", tempDirectory);
            return tempDirectory;
        }

        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        String dirName = PREFIX + timestamp;

        tempDirectory = Files.createTempDirectory(dirName);
        log.info("Created temp directory: {}", tempDirectory);

        // Add shutdown hook to clean up
        addShutdownHook();

        return tempDirectory;
    }

    /**
     * Get the current temp directory
     */
    public Path getTempDirectory() {
        return tempDirectory;
    }

    /**
     * Create subdirectory in temp directory
     */
    public Path createSubdirectory(String name) throws IOException {
        if (tempDirectory == null) {
            throw new IllegalStateException("Temp directory not created yet");
        }

        Path subdir = tempDirectory.resolve(name);
        if (!Files.exists(subdir)) {
            Files.createDirectories(subdir);
            log.debug("Created subdirectory: {}", subdir);
        }

        return subdir;
    }

    /**
     * Clean up temp directory
     */
    public void cleanup() {
        if (tempDirectory == null || !Files.exists(tempDirectory)) {
            return;
        }

        try {
            log.info("Cleaning up temp directory: {}", tempDirectory);
            deleteDirectoryRecursively(tempDirectory);
            tempDirectory = null;
        } catch (IOException e) {
            log.warn("Failed to delete temp directory: {}", tempDirectory, e);
        }
    }

    /**
     * Delete directory recursively
     */
    private void deleteDirectoryRecursively(Path directory) throws IOException {
        Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Add shutdown hook for cleanup
     */
    private void addShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.debug("Shutdown hook triggered, cleaning up temp directory");
            cleanup();
        }));
    }

    /**
     * Clean up old temp directories (older than 1 day)
     */
    public static void cleanupOldTempDirectories() {
        try {
            Path tempDir = Paths.get(System.getProperty("java.io.tmpdir"));

            Files.list(tempDir)
                .filter(p -> p.getFileName().toString().startsWith(PREFIX))
                .filter(p -> {
                    try {
                        long modified = Files.getLastModifiedTime(p).toMillis();
                        long age = System.currentTimeMillis() - modified;
                        return age > 24 * 60 * 60 * 1000; // > 1 day
                    } catch (IOException e) {
                        return false;
                    }
                })
                .forEach(p -> {
                    try {
                        TempDirectoryManager temp = new TempDirectoryManager();
                        temp.tempDirectory = p;
                        temp.cleanup();
                        log.debug("Cleaned up old temp directory: {}", p);
                    } catch (Exception e) {
                        log.warn("Failed to clean up old temp directory: {}", p, e);
                    }
                });
        } catch (IOException e) {
            log.warn("Failed to list temp directories", e);
        }
    }
}
