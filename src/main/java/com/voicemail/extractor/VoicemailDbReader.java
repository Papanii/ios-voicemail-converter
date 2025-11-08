package com.voicemail.extractor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads voicemail.db (voicemail metadata database)
 */
public class VoicemailDbReader {
    private static final Logger log = LoggerFactory.getLogger(VoicemailDbReader.class);

    private final Path voicemailDbPath;
    private Connection connection;

    public VoicemailDbReader(Path voicemailDbPath) {
        this.voicemailDbPath = voicemailDbPath;
    }

    /**
     * Open connection to voicemail.db
     */
    public void open() throws SQLException {
        log.debug("Opening voicemail.db: {}", voicemailDbPath);
        String url = "jdbc:sqlite:" + voicemailDbPath.toAbsolutePath();
        connection = DriverManager.getConnection(url);
    }

    /**
     * Close connection
     */
    public void close() {
        if (connection != null) {
            try {
                connection.close();
                log.debug("Closed voicemail.db connection");
            } catch (SQLException e) {
                log.warn("Error closing voicemail.db", e);
            }
        }
    }

    /**
     * Read all voicemail metadata
     */
    public List<VoicemailFile.VoicemailMetadata> readAllMetadata(boolean includeTrashed)
            throws SQLException {

        log.info("Reading voicemail metadata (includeTrashed={})", includeTrashed);

        List<VoicemailFile.VoicemailMetadata> metadataList = new ArrayList<>();

        String sql = "SELECT ROWID, remote_uid, date, sender, callback_num, " +
                    "       duration, expiration, trashed_date, flags " +
                    "FROM voicemail ";

        if (!includeTrashed) {
            sql += "WHERE trashed_date IS NULL ";
        }

        sql += "ORDER BY date DESC";

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                long rowId = rs.getLong("ROWID");
                long remoteUid = rs.getLong("remote_uid");

                // Date is Unix timestamp
                long dateUnix = rs.getLong("date");
                Instant receivedDate = Instant.ofEpochSecond(dateUnix);

                String sender = rs.getString("sender");
                String callbackNum = rs.getString("callback_num");
                int duration = rs.getInt("duration");

                // Expiration
                long expUnix = rs.getLong("expiration");
                Instant expirationDate = expUnix > 0 ? Instant.ofEpochSecond(expUnix) : null;

                // Trashed date
                long trashedUnix = rs.getLong("trashed_date");
                Instant trashedDate = trashedUnix > 0 ? Instant.ofEpochSecond(trashedUnix) : null;

                int flags = rs.getInt("flags");

                VoicemailFile.VoicemailMetadata metadata = new VoicemailFile.VoicemailMetadata(
                    rowId, remoteUid, receivedDate, sender, callbackNum,
                    duration, expirationDate, trashedDate, flags
                );

                metadataList.add(metadata);
                log.debug("Parsed metadata: {}", metadata);
            }
        }

        log.info("Read {} voicemail metadata records", metadataList.size());
        return metadataList;
    }

    /**
     * Check if database exists and has voicemail table
     */
    public static boolean isValidVoicemailDb(Path dbPath) {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
            DatabaseMetaData meta = conn.getMetaData();
            ResultSet rs = meta.getTables(null, null, "voicemail", null);
            return rs.next();
        } catch (SQLException e) {
            log.warn("Invalid voicemail.db: {}", dbPath, e);
            return false;
        }
    }
}
