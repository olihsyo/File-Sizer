package com.filesizer.app

import com.filesizer.app.feature.hexeditor.FileSignatureInspector
import org.junit.Assert.assertEquals
import org.junit.Test

class FileSignatureInspectorTest {
    @Test
    fun detectsCommonFileSignatures() {
        assertEquals("PNG image", FileSignatureInspector.detect(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)))
        assertEquals("ZIP archive", FileSignatureInspector.detect(byteArrayOf(0x50, 0x4b, 0x03, 0x04)))
        assertEquals("PDF document", FileSignatureInspector.detect("%PDF-".toByteArray()))
        assertEquals("Unknown or unrecognized signature", FileSignatureInspector.detect(byteArrayOf(0x00, 0x01, 0x02)))
    }
}
