package com.islandify.app.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionCompareTest {
    @Test fun newerMinor() = assertTrue(isNewerVersion("1.5", "1.4"))
    @Test fun sameVersion() = assertFalse(isNewerVersion("1.5", "1.5"))
    @Test fun trailingZeroIsSame() = assertFalse(isNewerVersion("1.5.0", "1.5"))
    @Test fun numericNotAlphabetical() = assertTrue(isNewerVersion("1.10", "1.9"))
    @Test fun olderIsNotNewer() = assertFalse(isNewerVersion("1.3", "1.5"))
    @Test fun patchLevel() = assertTrue(isNewerVersion("1.5.1", "1.5"))
    @Test fun noReleaseMeansNoUpdate() = assertFalse(isNewerVersion("0", "1.5"))
}
