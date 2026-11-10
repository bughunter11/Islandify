package com.islandify.app.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupGuardTest {
    @Test fun newKeyIsAccepted() = assertTrue(backupTypeMatches(null, "s"))
    @Test fun sameTypeIsAccepted() {
        assertTrue(backupTypeMatches(1.5f, "f"))
        assertTrue(backupTypeMatches(true, "b"))
        assertTrue(backupTypeMatches(7, "i"))
        assertTrue(backupTypeMatches(setOf("a"), "ss"))
    }
    @Test fun otherTypeIsRejected() {
        assertFalse(backupTypeMatches(1.5f, "s"))
        assertFalse(backupTypeMatches(true, "i"))
        assertFalse(backupTypeMatches("x", "ss"))
    }
}
