package com.voicemail.backup;

import com.dd.plist.NSDictionary;
import com.dd.plist.NSDate;
import com.dd.plist.PropertyListParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

/**
 * Parser for Apple plist files (Info.plist, Manifest.plist)
 */
public class PlistParser {
    private static final Logger log = LoggerFactory.getLogger(PlistParser.class);

    /**
     * Parse Info.plist and extract device information
     */
    public static BackupInfo.Builder parseInfoPlist(Path infoPlist, Path backupPath) throws Exception {
        log.debug("Parsing Info.plist: {}", infoPlist);

        NSDictionary rootDict = (NSDictionary) PropertyListParser.parse(infoPlist.toFile());
        BackupInfo.Builder builder = new BackupInfo.Builder();

        builder.backupPath(backupPath);

        // Extract fields
        if (rootDict.containsKey("Device Name")) {
            builder.deviceName(rootDict.get("Device Name").toString());
        }

        if (rootDict.containsKey("Display Name")) {
            builder.displayName(rootDict.get("Display Name").toString());
        }

        if (rootDict.containsKey("Product Type")) {
            builder.productType(rootDict.get("Product Type").toString());
        }

        if (rootDict.containsKey("Product Version")) {
            builder.productVersion(rootDict.get("Product Version").toString());
        }

        if (rootDict.containsKey("Serial Number")) {
            builder.serialNumber(rootDict.get("Serial Number").toString());
        }

        if (rootDict.containsKey("Phone Number")) {
            builder.phoneNumber(rootDict.get("Phone Number").toString());
        }

        if (rootDict.containsKey("Unique Identifier")) {
            builder.udid(rootDict.get("Unique Identifier").toString());
        } else if (rootDict.containsKey("UDID")) {
            builder.udid(rootDict.get("UDID").toString());
        } else {
            // Use directory name as fallback
            builder.udid(backupPath.getFileName().toString());
        }

        if (rootDict.containsKey("Last Backup Date")) {
            NSDate lastBackup = (NSDate) rootDict.get("Last Backup Date");
            builder.lastBackupDate(convertNSDateToLocalDateTime(lastBackup));
        }

        log.debug("Parsed device: {}", rootDict.get("Device Name"));
        return builder;
    }

    /**
     * Parse Manifest.plist and extract backup metadata
     */
    public static void parseManifestPlist(Path manifestPlist, BackupInfo.Builder builder) throws Exception {
        log.debug("Parsing Manifest.plist: {}", manifestPlist);

        NSDictionary rootDict = (NSDictionary) PropertyListParser.parse(manifestPlist.toFile());

        // Check encryption status
        if (rootDict.containsKey("IsEncrypted")) {
            boolean encrypted = Boolean.parseBoolean(rootDict.get("IsEncrypted").toString());
            builder.isEncrypted(encrypted);
            log.debug("Backup encrypted: {}", encrypted);
        }

        // Extract backup date from Manifest if not in Info
        if (rootDict.containsKey("Date")) {
            NSDate backupDate = (NSDate) rootDict.get("Date");
            builder.lastBackupDate(convertNSDateToLocalDateTime(backupDate));
        }
    }

    /**
     * Convert NSDate to LocalDateTime
     */
    private static LocalDateTime convertNSDateToLocalDateTime(NSDate nsDate) {
        Date javaDate = nsDate.getDate();
        return LocalDateTime.ofInstant(javaDate.toInstant(), ZoneId.systemDefault());
    }

    /**
     * Check if plist file is valid
     */
    public static boolean isValidPlist(Path plistFile) {
        try {
            PropertyListParser.parse(plistFile.toFile());
            return true;
        } catch (Exception e) {
            log.warn("Invalid plist file: {}", plistFile, e);
            return false;
        }
    }
}
