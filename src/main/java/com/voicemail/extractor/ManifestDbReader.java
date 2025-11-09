package com.voicemail.extractor;

import com.voicemail.util.HashUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads iOS Manifest.db (SQLite file catalog)
 */
public class ManifestDbReader {
    private static final Logger log = LoggerFactory.getLogger(ManifestDbReader.class);

    private final Path manifestDbPath;
    private Connection connection;

    public ManifestDbReader(Path backupPath) {
        this.manifestDbPath = backupPath.resolve("Manifest.db");
    }

    /**
     * Open connection to Manifest.db
     */
    public void open() throws SQLException {
        log.debug("Opening Manifest.db: {}", manifestDbPath);
        String url = "jdbc:sqlite:" + manifestDbPath.toAbsolutePath();
        connection = DriverManager.getConnection(url);
    }

    /**
     * Close connection
     */
    public void close() {
        if (connection != null) {
            try {
                connection.close();
                log.debug("Closed Manifest.db connection");
            } catch (SQLException e) {
                log.warn("Error closing Manifest.db", e);
            }
        }
    }

    /**
     * Find file in manifest by domain and relative path
     */
    public String findFileId(String domain, String relativePath) throws SQLException {
        String hash = HashUtil.calculateBackupFileHash(domain, relativePath);
        log.debug("Looking for file: {} (hash: {})", relativePath, hash);

        String sql = "SELECT fileID FROM Files WHERE fileID = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, hash);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                String fileId = rs.getString("fileID");
                log.debug("Found file: {}", fileId);
                return fileId;
            }
        }

        log.debug("File not found in manifest: {}", relativePath);
        return null;
    }

    /**
     * Query voicemail.db file info
     */
    public FileInfo queryVoicemailDbFile() throws SQLException {
        log.info("Querying voicemail.db from Manifest.db");

        // Try queries in order from most specific to most general
        String[] queries = {
            // Most specific - exact match with correct domain and path (iOS 10+)
            "SELECT fileID, domain, relativePath FROM Files WHERE domain = 'HomeDomain' AND relativePath = 'Library/Voicemail/voicemail.db'",
            // Pattern match on path with directory (catches path variations)
            "SELECT fileID, domain, relativePath FROM Files WHERE relativePath LIKE '%/Voicemail/voicemail.db'",
            // Broad match - any file ending in voicemail.db (last resort)
            "SELECT fileID, domain, relativePath FROM Files WHERE relativePath LIKE '%voicemail.db'"
        };

        for (String sql : queries) {
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {

                if (rs.next()) {
                    String fileId = rs.getString("fileID");
                    String domain = rs.getString("domain");
                    String relativePath = rs.getString("relativePath");

                    log.info("Found voicemail.db: domain={}, relativePath={}, fileID={}",
                            domain, relativePath, fileId);
                    return new FileInfo(fileId, domain, relativePath);
                }
            }
        }

        log.warn("voicemail.db not found in Manifest.db");
        log.info("Attempting to list all database files for debugging...");

        // Debug: List all .db files in the manifest
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT fileID, domain, relativePath FROM Files WHERE relativePath LIKE '%.db' LIMIT 10")) {

            log.debug("Sample .db files in backup:");
            while (rs.next()) {
                log.debug("  {}: {}", rs.getString("domain"), rs.getString("relativePath"));
            }
        }

        return null;
    }

    /**
     * List all files in Library-Voicemail domain for debugging
     */
    public List<FileInfo> listLibraryVoicemailFiles() throws SQLException {
        log.info("Listing all files in Library-Voicemail domain");

        List<FileInfo> files = new ArrayList<>();

        String sql = "SELECT fileID, domain, relativePath " +
                    "FROM Files " +
                    "WHERE domain LIKE '%Voicemail%' " +
                    "ORDER BY relativePath";

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                String fileId = rs.getString("fileID");
                String domain = rs.getString("domain");
                String relativePath = rs.getString("relativePath");

                files.add(new FileInfo(fileId, domain, relativePath));
                log.debug("  {}: {}", domain, relativePath);
            }
        }

        log.info("Found {} files in voicemail-related domains", files.size());
        return files;
    }

    /**
     * Query all voicemail audio files
     */
    public List<FileInfo> queryVoicemailFiles() throws SQLException {
        log.info("Querying voicemail files from Manifest.db");

        List<FileInfo> files = new ArrayList<>();

        // Try most specific query first - HomeDomain with Library/Voicemail/ path (iOS 10+)
        String sql = "SELECT fileID, domain, relativePath " +
                    "FROM Files " +
                    "WHERE domain = 'HomeDomain' " +
                    "  AND relativePath LIKE 'Library/Voicemail/%' " +
                    "  AND (relativePath LIKE '%.amr' " +
                    "       OR relativePath LIKE '%.awb' " +
                    "       OR relativePath LIKE '%.m4a') " +
                    "ORDER BY relativePath";

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                String fileId = rs.getString("fileID");
                String domain = rs.getString("domain");
                String relativePath = rs.getString("relativePath");

                files.add(new FileInfo(fileId, domain, relativePath));
                log.debug("Found voicemail file: {} (domain: {})", relativePath, domain);
            }
        }

        // If no files found with specific query, try fallback patterns
        if (files.isEmpty()) {
            log.info("No files found with HomeDomain/Library/Voicemail/, trying fallback queries...");

            // Fallback: Any path containing /Voicemail/ with audio files
            String fallbackSql = "SELECT fileID, domain, relativePath " +
                                "FROM Files " +
                                "WHERE relativePath LIKE '%/Voicemail/%' " +
                                "  AND (relativePath LIKE '%.amr' " +
                                "       OR relativePath LIKE '%.awb' " +
                                "       OR relativePath LIKE '%.m4a') " +
                                "ORDER BY relativePath";

            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(fallbackSql)) {

                while (rs.next()) {
                    String fileId = rs.getString("fileID");
                    String domain = rs.getString("domain");
                    String relativePath = rs.getString("relativePath");

                    files.add(new FileInfo(fileId, domain, relativePath));
                    log.debug("Found voicemail file (fallback): {} (domain: {})", relativePath, domain);
                }
            }
        }

        log.info("Found {} voicemail audio files", files.size());

        // If still no files found, log debugging information
        if (files.isEmpty()) {
            log.warn("No voicemail audio files found with any query pattern");
            log.info("Checking for any audio files in backup...");

            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(*) as cnt FROM Files WHERE relativePath LIKE '%.amr' OR relativePath LIKE '%.awb' OR relativePath LIKE '%.m4a'")) {

                if (rs.next()) {
                    int count = rs.getInt("cnt");
                    log.info("Total audio files (.amr/.awb/.m4a) in backup: {}", count);
                }
            }

            // List some sample files from HomeDomain
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(
                     "SELECT domain, relativePath FROM Files WHERE domain = 'HomeDomain' AND relativePath LIKE 'Library/%' LIMIT 20")) {

                log.debug("Sample Library files in HomeDomain:");
                while (rs.next()) {
                    log.debug("  {}: {}", rs.getString("domain"), rs.getString("relativePath"));
                }
            }
        }

        return files;
    }

    /**
     * Simple data class for file information
     */
    public static class FileInfo {
        public final String fileId;
        public final String domain;
        public final String relativePath;

        public FileInfo(String fileId, String domain, String relativePath) {
            this.fileId = fileId;
            this.domain = domain;
            this.relativePath = relativePath;
        }

        @Override
        public String toString() {
            return String.format("FileInfo[%s: %s]", fileId, relativePath);
        }
    }
}
