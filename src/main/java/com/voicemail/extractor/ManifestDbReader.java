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
     * Query all voicemail audio files
     */
    public List<FileInfo> queryVoicemailFiles() throws SQLException {
        log.info("Querying voicemail files from Manifest.db");

        List<FileInfo> files = new ArrayList<>();

        String sql = "SELECT fileID, domain, relativePath " +
                    "FROM Files " +
                    "WHERE domain = 'Library-Voicemail' " +
                    "  AND (relativePath LIKE 'voicemail/%.amr' " +
                    "       OR relativePath LIKE 'voicemail/%.awb' " +
                    "       OR relativePath LIKE 'voicemail/%.m4a') " +
                    "ORDER BY relativePath";

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                String fileId = rs.getString("fileID");
                String domain = rs.getString("domain");
                String relativePath = rs.getString("relativePath");

                files.add(new FileInfo(fileId, domain, relativePath));
                log.debug("Found voicemail file: {}", relativePath);
            }
        }

        log.info("Found {} voicemail audio files", files.size());
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
