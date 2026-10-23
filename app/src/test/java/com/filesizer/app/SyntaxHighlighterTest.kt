package com.filesizer.app

import com.filesizer.app.core.editor.SyntaxHighlighter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyntaxHighlighterTest {

    @Test
    fun testKotlinHighlighting() {
        val code = "fun main() {\n    val name = \"File Sizer\"\n    println(name)\n}"
        val highlighted = SyntaxHighlighter.highlight(code, "kt")
        assertEquals(code, highlighted.text)
        assertTrue(highlighted.spanStyles.isNotEmpty())
    }

    @Test
    fun testJsonHighlighting() {
        val json = "{\"name\": \"File Sizer\", \"active\": true, \"count\": 42}"
        val highlighted = SyntaxHighlighter.highlight(json, "json")
        assertEquals(json, highlighted.text)
        assertTrue(highlighted.spanStyles.isNotEmpty())
    }

    @Test
    fun testXmlHighlighting() {
        val xml = "<manifest package=\"com.filesizer.app\">\n    <application />\n</manifest>"
        val highlighted = SyntaxHighlighter.highlight(xml, "xml")
        assertEquals(xml, highlighted.text)
        assertTrue(highlighted.spanStyles.isNotEmpty())
    }
}
