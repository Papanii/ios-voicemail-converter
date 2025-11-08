package com.voicemail.backup;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Immutable data class containing iOS backup metadata
 */
public class BackupInfo {
    private final String udid;
    private final String deviceName;
    private final String displayName;
    private final String productType;
    private final String productVersion;  // iOS version
    private final String serialNumber;
    private final String phoneNumber;
    private final LocalDateTime lastBackupDate;
    private final boolean isEncrypted;
    private final Path backupPath;

    private BackupInfo(Builder builder) {
        this.udid = Objects.requireNonNull(builder.udid, "udid is required");
        this.deviceName = builder.deviceName;
        this.displayName = builder.displayName;
        this.productType = builder.productType;
        this.productVersion = builder.productVersion;
        this.serialNumber = builder.serialNumber;
        this.phoneNumber = builder.phoneNumber;
        this.lastBackupDate = builder.lastBackupDate;
        this.isEncrypted = builder.isEncrypted;
        this.backupPath = Objects.requireNonNull(builder.backupPath, "backupPath is required");
    }

    // Getters
    public String getUdid() { return udid; }
    public String getDeviceName() { return deviceName; }
    public String getDisplayName() { return displayName; }
    public String getProductType() { return productType; }
    public String getProductVersion() { return productVersion; }
    public String getSerialNumber() { return serialNumber; }
    public String getPhoneNumber() { return phoneNumber; }
    public LocalDateTime getLastBackupDate() { return lastBackupDate; }
    public boolean isEncrypted() { return isEncrypted; }
    public Path getBackupPath() { return backupPath; }

    /**
     * Get friendly device description (e.g., "iPhone 13 Pro")
     */
    public String getDeviceDescription() {
        if (productType != null) {
            return mapProductTypeToName(productType);
        }
        return deviceName != null ? deviceName : "Unknown Device";
    }

    /**
     * Map product type to friendly name
     */
    private String mapProductTypeToName(String productType) {
        // Common mappings
        switch (productType) {
            case "iPhone14,2": return "iPhone 13 Pro";
            case "iPhone14,3": return "iPhone 13 Pro Max";
            case "iPhone14,4": return "iPhone 13 mini";
            case "iPhone14,5": return "iPhone 13";
            case "iPhone15,2": return "iPhone 14 Pro";
            case "iPhone15,3": return "iPhone 14 Pro Max";
            case "iPhone15,4": return "iPhone 14";
            case "iPhone15,5": return "iPhone 14 Plus";
            case "iPhone16,1": return "iPhone 15 Pro";
            case "iPhone16,2": return "iPhone 15 Pro Max";
            // Add more mappings as needed
            default: return productType;
        }
    }

    @Override
    public String toString() {
        return String.format("%s (iOS %s) - Last backup: %s [%s]",
            deviceName != null ? deviceName : "Unknown",
            productVersion != null ? productVersion : "Unknown",
            lastBackupDate != null ? lastBackupDate : "Unknown",
            udid
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BackupInfo)) return false;
        BackupInfo that = (BackupInfo) o;
        return Objects.equals(udid, that.udid) &&
               Objects.equals(backupPath, that.backupPath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(udid, backupPath);
    }

    /**
     * Builder for BackupInfo
     */
    public static class Builder {
        private String udid;
        private String deviceName;
        private String displayName;
        private String productType;
        private String productVersion;
        private String serialNumber;
        private String phoneNumber;
        private LocalDateTime lastBackupDate;
        private boolean isEncrypted;
        private Path backupPath;

        public Builder udid(String udid) {
            this.udid = udid;
            return this;
        }

        public Builder deviceName(String deviceName) {
            this.deviceName = deviceName;
            return this;
        }

        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder productType(String productType) {
            this.productType = productType;
            return this;
        }

        public Builder productVersion(String productVersion) {
            this.productVersion = productVersion;
            return this;
        }

        public Builder serialNumber(String serialNumber) {
            this.serialNumber = serialNumber;
            return this;
        }

        public Builder phoneNumber(String phoneNumber) {
            this.phoneNumber = phoneNumber;
            return this;
        }

        public Builder lastBackupDate(LocalDateTime lastBackupDate) {
            this.lastBackupDate = lastBackupDate;
            return this;
        }

        public Builder isEncrypted(boolean isEncrypted) {
            this.isEncrypted = isEncrypted;
            return this;
        }

        public Builder backupPath(Path backupPath) {
            this.backupPath = backupPath;
            return this;
        }

        public BackupInfo build() {
            return new BackupInfo(this);
        }
    }
}
