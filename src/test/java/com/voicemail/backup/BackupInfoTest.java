package com.voicemail.backup;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class BackupInfoTest {

    @Test
    void testBackupInfoBuilder() {
        BackupInfo info = new BackupInfo.Builder()
            .udid("1234567890abcdef1234567890abcdef12345678")
            .deviceName("Test iPhone")
            .productVersion("17.5")
            .lastBackupDate(LocalDateTime.now())
            .isEncrypted(false)
            .backupPath(Paths.get("/tmp/backup"))
            .build();

        assertNotNull(info);
        assertEquals("Test iPhone", info.getDeviceName());
        assertEquals("17.5", info.getProductVersion());
        assertFalse(info.isEncrypted());
        assertEquals("1234567890abcdef1234567890abcdef12345678", info.getUdid());
    }

    @Test
    void testBackupInfoRequiredFields() {
        BackupInfo.Builder builder = new BackupInfo.Builder()
            .deviceName("Test iPhone");

        // Missing required fields: udid and backupPath
        assertThrows(NullPointerException.class, builder::build);
    }

    @Test
    void testBackupInfoMissingUdid() {
        BackupInfo.Builder builder = new BackupInfo.Builder()
            .backupPath(Paths.get("/tmp"));

        // Missing required field: udid
        assertThrows(NullPointerException.class, builder::build);
    }

    @Test
    void testBackupInfoMissingBackupPath() {
        BackupInfo.Builder builder = new BackupInfo.Builder()
            .udid("test-udid");

        // Missing required field: backupPath
        assertThrows(NullPointerException.class, builder::build);
    }

    @Test
    void testProductTypeMapping() {
        BackupInfo info = new BackupInfo.Builder()
            .udid("test")
            .productType("iPhone14,2")
            .backupPath(Paths.get("/tmp"))
            .build();

        assertEquals("iPhone 13 Pro", info.getDeviceDescription());
    }

    @Test
    void testProductTypeMappingUnknown() {
        BackupInfo info = new BackupInfo.Builder()
            .udid("test")
            .productType("iPhone99,9")
            .backupPath(Paths.get("/tmp"))
            .build();

        assertEquals("iPhone99,9", info.getDeviceDescription());
    }

    @Test
    void testDeviceDescriptionFallback() {
        BackupInfo info = new BackupInfo.Builder()
            .udid("test")
            .deviceName("My iPhone")
            .backupPath(Paths.get("/tmp"))
            .build();

        assertEquals("My iPhone", info.getDeviceDescription());
    }

    @Test
    void testDeviceDescriptionDefault() {
        BackupInfo info = new BackupInfo.Builder()
            .udid("test")
            .backupPath(Paths.get("/tmp"))
            .build();

        assertEquals("Unknown Device", info.getDeviceDescription());
    }

    @Test
    void testToString() {
        LocalDateTime now = LocalDateTime.now();
        BackupInfo info = new BackupInfo.Builder()
            .udid("test-udid-123")
            .deviceName("Test iPhone")
            .productVersion("17.5")
            .lastBackupDate(now)
            .backupPath(Paths.get("/tmp"))
            .build();

        String str = info.toString();

        assertTrue(str.contains("Test iPhone"));
        assertTrue(str.contains("17.5"));
        assertTrue(str.contains("test-udid-123"));
    }

    @Test
    void testEquals() {
        Path path1 = Paths.get("/tmp/backup1");
        Path path2 = Paths.get("/tmp/backup2");

        BackupInfo info1 = new BackupInfo.Builder()
            .udid("udid1")
            .backupPath(path1)
            .build();

        BackupInfo info2 = new BackupInfo.Builder()
            .udid("udid1")
            .backupPath(path1)
            .build();

        BackupInfo info3 = new BackupInfo.Builder()
            .udid("udid2")
            .backupPath(path2)
            .build();

        assertEquals(info1, info2);
        assertNotEquals(info1, info3);
    }

    @Test
    void testHashCode() {
        Path path = Paths.get("/tmp/backup");

        BackupInfo info1 = new BackupInfo.Builder()
            .udid("udid")
            .backupPath(path)
            .build();

        BackupInfo info2 = new BackupInfo.Builder()
            .udid("udid")
            .backupPath(path)
            .build();

        assertEquals(info1.hashCode(), info2.hashCode());
    }

    @Test
    void testAllOptionalFields() {
        LocalDateTime now = LocalDateTime.now();

        BackupInfo info = new BackupInfo.Builder()
            .udid("test-udid")
            .deviceName("iPhone 13 Pro")
            .displayName("John's iPhone")
            .productType("iPhone14,2")
            .productVersion("17.5.1")
            .serialNumber("ABC123XYZ")
            .phoneNumber("+1234567890")
            .lastBackupDate(now)
            .isEncrypted(true)
            .backupPath(Paths.get("/tmp/backup"))
            .build();

        assertEquals("test-udid", info.getUdid());
        assertEquals("iPhone 13 Pro", info.getDeviceName());
        assertEquals("John's iPhone", info.getDisplayName());
        assertEquals("iPhone14,2", info.getProductType());
        assertEquals("17.5.1", info.getProductVersion());
        assertEquals("ABC123XYZ", info.getSerialNumber());
        assertEquals("+1234567890", info.getPhoneNumber());
        assertEquals(now, info.getLastBackupDate());
        assertTrue(info.isEncrypted());
        assertEquals(Paths.get("/tmp/backup"), info.getBackupPath());
    }
}
