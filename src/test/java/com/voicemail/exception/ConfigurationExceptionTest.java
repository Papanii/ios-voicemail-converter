package com.voicemail.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConfigurationExceptionTest {

    @Test
    void testBasicConstructor() {
        ConfigurationException ex = new ConfigurationException("Test error");

        assertEquals("Test error", ex.getMessage());
        assertEquals(2, ex.getExitCode()); // Default
        assertFalse(ex.hasSuggestion());
        assertNull(ex.getSuggestion());
    }

    @Test
    void testWithExitCode() {
        ConfigurationException ex = new ConfigurationException("Test error", 5);

        assertEquals("Test error", ex.getMessage());
        assertEquals(5, ex.getExitCode());
        assertFalse(ex.hasSuggestion());
    }

    @Test
    void testWithSuggestion() {
        ConfigurationException ex = new ConfigurationException(
            "Test error", 2, "Try this fix"
        );

        assertEquals("Test error", ex.getMessage());
        assertEquals(2, ex.getExitCode());
        assertTrue(ex.hasSuggestion());
        assertEquals("Try this fix", ex.getSuggestion());
    }

    @Test
    void testWithEmptySuggestion() {
        ConfigurationException ex = new ConfigurationException(
            "Test error", 2, ""
        );

        assertFalse(ex.hasSuggestion());
    }

    @Test
    void testWithNullSuggestion() {
        ConfigurationException ex = new ConfigurationException(
            "Test error", 2, null
        );

        assertFalse(ex.hasSuggestion());
        assertNull(ex.getSuggestion());
    }

    @Test
    void testExtendsException() {
        ConfigurationException ex = new ConfigurationException("Test");

        assertTrue(ex instanceof Exception);
    }
}
