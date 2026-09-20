package io.github.rhythmcache.dioxamine.core

import org.junit.Assert.*
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun `isNewerVersion returns false for same version`() {
        assertFalse(isNewerVersion("v0.0.3-stable", "0.0.3-stable"))
        assertFalse(isNewerVersion("0.0.3-stable", "0.0.3-stable"))
    }

    @Test
    fun `isNewerVersion returns true for higher patch, minor, or major`() {
        assertTrue(isNewerVersion("v0.0.4-stable", "0.0.3-stable"))
        assertTrue(isNewerVersion("v0.0.4", "0.0.3-stable"))
        assertTrue(isNewerVersion("v0.1.0", "0.0.3-stable"))
        assertTrue(isNewerVersion("v1.0.0", "0.0.3-stable"))
        assertTrue(isNewerVersion("v0.0.3.1", "0.0.3-stable"))
    }

    @Test
    fun `isNewerVersion returns false for older version`() {
        assertFalse(isNewerVersion("v0.0.2-stable", "0.0.3-stable"))
        assertFalse(isNewerVersion("v0.0.1", "0.0.3-stable"))
    }

    @Test
    fun `isNewerVersion handles transition to clean v0 0 x format`() {
        // Upgrading from 0.0.3-stable to v0.0.4
        assertTrue(isNewerVersion("v0.0.4", "0.0.3-stable"))
        // Same version number (0.0.3 tag vs 0.0.3-stable local) should not falsely alert
        assertFalse(isNewerVersion("v0.0.3", "0.0.3-stable"))
        // Clean v0.0.x version comparing with clean local version
        assertFalse(isNewerVersion("v0.0.4", "0.0.4"))
        assertTrue(isNewerVersion("v0.0.5", "0.0.4"))
        assertFalse(isNewerVersion("v0.0.3", "0.0.4"))
    }
}
