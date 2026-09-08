package com.shrutimonitor.app.data.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionComparatorTest {

    @Test
    fun testPatchIncrementIsNewer() {
        assertTrue(VersionComparator.isNewer("1.1.2", "1.1.1"))
        assertTrue(VersionComparator.isNewer("v1.1.2", "1.1.1"))
        assertTrue(VersionComparator.isNewer("V1.1.2", "1.1.1"))
        assertTrue(VersionComparator.isNewer("1.1.2", "v1.1.1"))
    }

    @Test
    fun testMinorIncrementIsNewer() {
        assertTrue(VersionComparator.isNewer("1.2.0", "1.1.1"))
        assertTrue(VersionComparator.isNewer("v1.2.0", "v1.1.9"))
    }

    @Test
    fun testMajorIncrementIsNewer() {
        assertTrue(VersionComparator.isNewer("2.0.0", "1.9.9"))
        assertTrue(VersionComparator.isNewer("v2.0.0", "1.1.1"))
    }

    @Test
    fun testSameVersionIsNotNewer() {
        assertFalse(VersionComparator.isNewer("1.1.1", "1.1.1"))
        assertFalse(VersionComparator.isNewer("v1.1.1", "1.1.1"))
        assertFalse(VersionComparator.isNewer("1.1.1", "v1.1.1"))
    }

    @Test
    fun testOlderVersionIsNotNewer() {
        assertFalse(VersionComparator.isNewer("1.1.0", "1.1.1"))
        assertFalse(VersionComparator.isNewer("1.0.9", "1.1.1"))
        assertFalse(VersionComparator.isNewer("0.9.0", "1.1.1"))
    }

    @Test
    fun testVariableLengthComponents() {
        assertTrue(VersionComparator.isNewer("1.1.1.1", "1.1.1"))
        assertFalse(VersionComparator.isNewer("1.1", "1.1.1"))
        assertTrue(VersionComparator.isNewer("1.2", "1.1.1"))
    }
}
