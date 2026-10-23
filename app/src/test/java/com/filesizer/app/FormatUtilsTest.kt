package com.filesizer.app

import com.filesizer.app.core.designsystem.components.formatFileSize
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatUtilsTest {

    @Test
    fun testFormatZeroBytes() {
        assertEquals("0 B", formatFileSize(0L))
        assertEquals("0 B", formatFileSize(-100L))
    }

    @Test
    fun testFormatKilobytes() {
        assertEquals("1.0 KB", formatFileSize(1024L))
        assertEquals("1.5 KB", formatFileSize(1536L))
    }

    @Test
    fun testFormatMegabytes() {
        assertEquals("1.0 MB", formatFileSize(1024L * 1024L))
        assertEquals("500.0 MB", formatFileSize(500L * 1024L * 1024L))
    }

    @Test
    fun testFormatGigabytes() {
        assertEquals("1.0 GB", formatFileSize(1024L * 1024L * 1024L))
        assertEquals("64.0 GB", formatFileSize(64L * 1024L * 1024L * 1024L))
    }
}
