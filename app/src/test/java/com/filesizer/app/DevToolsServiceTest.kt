package com.filesizer.app

import com.filesizer.app.core.devtools.DevToolsService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DevToolsServiceTest {

    @Test
    fun testHashCalculations() {
        val input = "Hello World"
        // MD5 of "Hello World" is b10a8db164e0754105b7a99be72e3fe5
        val md5 = DevToolsService.calculateHash(input, "MD5")
        assertEquals("b10a8db164e0754105b7a99be72e3fe5", md5)

        // SHA-256 of "Hello World" is a591a6d40bf420404a011733cfb7b190d62c65bf0bcda32b57b277d9ad9f146e
        val sha256 = DevToolsService.calculateHash(input, "SHA-256")
        assertEquals("a591a6d40bf420404a011733cfb7b190d62c65bf0bcda32b57b277d9ad9f146e", sha256)
    }

    @Test
    fun testBase64EncodingAndDecoding() {
        val original = "File Sizer Advanced Android Studio"
        val encoded = DevToolsService.base64Encode(original)
        val decoded = DevToolsService.base64Decode(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun testUrlEncodingAndDecoding() {
        val original = "path/to file?name=File Sizer&version=2.0"
        val encoded = DevToolsService.urlEncode(original)
        val decoded = DevToolsService.urlDecode(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun testJsonFormatting() {
        val messyJson = "{\"app\":\"File Sizer\",\"tags\":[\"fast\",\"safe\"],\"ready\":true}"
        val result = DevToolsService.formatJson(messyJson)
        assertTrue(result.isSuccess)
        val formatted = result.getOrNull()!!
        assertTrue(formatted.contains("\n"))
        assertTrue(formatted.contains("  \"app\": \"File Sizer\""))
    }
}
