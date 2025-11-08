package com.voicemail.util;

import com.voicemail.exception.InsufficientStorageException;
import com.voicemail.exception.PermissionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Utility for file system operations
 */
public class FileSystemUtil {
    private static final Logger log = LoggerFactory.getLogger(FileSystemUtil.class);

    /**
     * Ensure directory exists, create if necessary
     */
    public static void ensureDirectoryExists(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            log.debug("Creating directory: {}", directory);
            Files.createDirectories(directory);
        } else if (!Files.isDirectory(directory)) {
            throw new IOException("Path exists but is not a directory: " + directory);
        }
    }

    /**
     * Check if path is readable
     */
    public static void ensureReadable(Path path) throws PermissionException {
        if (!Files.isReadable(path)) {
            throw new PermissionException(path, PermissionException.PermissionType.READ);
        }
    }

    /**
     * Check if path is writable
     */
    public static void ensureWritable(Path path) throws PermissionException {
        if (!Files.isWritable(path)) {
            throw new PermissionException(path, PermissionException.PermissionType.WRITE);
        }
    }

    /**
     * Check if sufficient disk space is available
     */
    public static void checkDiskSpace(Path location, long requiredBytes)
            throws IOException, InsufficientStorageException {

        long available = Files.getFileStore(location).getUsableSpace();

        if (available < requiredBytes) {
            throw new InsufficientStorageException(location, requiredBytes, available);
        }
    }

    /**
     * Get available disk space
     */
    public static long getAvailableSpace(Path location) throws IOException {
        return Files.getFileStore(location).getUsableSpace();
    }

    /**
     * Copy file with progress logging
     */
    public static void copyFile(Path source, Path destination) throws IOException {
        log.debug("Copying: {} -> {}", source, destination);

        // Ensure parent directory exists
        Path parent = destination.getParent();
        if (parent != null) {
            ensureDirectoryExists(parent);
        }

        Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Move file
     */
    public static void moveFile(Path source, Path destination) throws IOException {
        log.debug("Moving: {} -> {}", source, destination);

        Path parent = destination.getParent();
        if (parent != null) {
            ensureDirectoryExists(parent);
        }

        Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Calculate directory size recursively
     */
    public static long calculateDirectorySize(Path directory) throws IOException {
        final long[] size = {0};

        Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                size[0] += attrs.size();
                return FileVisitResult.CONTINUE;
            }
        });

        return size[0];
    }

    /**
     * Count files in directory (non-recursive)
     */
    public static long countFiles(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return 0;
        }

        try (var stream = Files.list(directory)) {
            return stream.filter(Files::isRegularFile).count();
        }
    }

    /**
     * Generate unique filename if file exists
     */
    public static Path generateUniqueFilename(Path path) {
        if (!Files.exists(path)) {
            return path;
        }

        String fileName = path.getFileName().toString();
        Path parent = path.getParent();

        // Split name and extension
        int dotIndex = fileName.lastIndexOf('.');
        String name = dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
        String ext = dotIndex > 0 ? fileName.substring(dotIndex) : "";

        // Try appending _001, _002, etc.
        int counter = 1;
        Path newPath;
        do {
            String newFileName = String.format("%s_%03d%s", name, counter, ext);
            newPath = parent != null ? parent.resolve(newFileName) : Paths.get(newFileName);
            counter++;
        } while (Files.exists(newPath) && counter < 1000);

        if (Files.exists(newPath)) {
            throw new IllegalStateException("Could not generate unique filename after 1000 attempts");
        }

        return newPath;
    }

    /**
     * Check if file is empty
     */
    public static boolean isEmpty(Path file) throws IOException {
        if (!Files.exists(file)) {
            return true;
        }
        return Files.size(file) == 0;
    }
}
