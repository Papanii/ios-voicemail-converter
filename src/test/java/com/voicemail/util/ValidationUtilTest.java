package com.voicemail.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ValidationUtilTest {

    @Test
    void testValidUdidHexFormat() {
        String validHex = "1234567890abcdef1234567890abcdef12345678";
        assertTrue(ValidationUtil.isValidUdid(validHex));
    }

    @Test
    void testValidUdidUuidFormat() {
        String validUuid = "12345678-1234-1234-1234-123456789012";
        assertTrue(ValidationUtil.isValidUdid(validUuid));
    }

    @Test
    void testInvalidUdidTooShort() {
        String invalid = "1234567890abcdef";
        assertFalse(ValidationUtil.isValidUdid(invalid));
    }

    @Test
    void testInvalidUdidTooLong() {
        String invalid = "1234567890abcdef1234567890abcdef123456789";
        assertFalse(ValidationUtil.isValidUdid(invalid));
    }

    @Test
    void testInvalidUdidNonHex() {
        String invalid = "12345678901234567890123456789012345678GZ";
        assertFalse(ValidationUtil.isValidUdid(invalid));
    }

    @Test
    void testNullUdid() {
        assertFalse(ValidationUtil.isValidUdid(null));
    }

    @Test
    void testEmptyUdid() {
        assertFalse(ValidationUtil.isValidUdid(""));
    }

    @Test
    void testValidUdidUppercase() {
        String validUpper = "ABCDEF1234567890ABCDEF1234567890ABCDEF12";
        assertTrue(ValidationUtil.isValidUdid(validUpper));
    }

    @Test
    void testValidUdidMixedCase() {
        String validMixed = "AbCdEf1234567890aBcDeF1234567890AbCdEf12";
        assertTrue(ValidationUtil.isValidUdid(validMixed));
    }

    @Test
    void testValidUdidAppleFormat() {
        // Real Apple device UUID format (8-4-4-4-4)
        String appleUuid = "00008140-0001-688C-0213-001C";
        assertTrue(ValidationUtil.isValidUdid(appleUuid));
    }

    @Test
    void testValidUdidAppleFormatLowercase() {
        String appleUuid = "00008140-0001-688c-0213-001c";
        assertTrue(ValidationUtil.isValidUdid(appleUuid));
    }

    @Test
    void testValidUdidAppleFormatMixedCase() {
        String appleUuid = "00008140-0001-688C-0213-001c";
        assertTrue(ValidationUtil.isValidUdid(appleUuid));
    }

    @Test
    void testInvalidUdidWrongAppleFormat() {
        // Wrong format: missing segment
        String invalid = "00008140-0001-688C-0213";
        assertFalse(ValidationUtil.isValidUdid(invalid));
    }
}
