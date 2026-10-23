package com.filesizer.app.core.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import java.util.regex.Pattern

object SyntaxHighlighter {

    private val KEYWORD_COLOR = Color(0xFFC678DD)    // Purple
    private val STRING_COLOR = Color(0xFF98C379)     // Green
    private val NUMBER_COLOR = Color(0xFFD19A66)     // Orange
    private val COMMENT_COLOR = Color(0xFF7F848E)    // Gray
    private val TAG_COLOR = Color(0xFFE06C75)        // Red/Pink
    private val ATTRIBUTE_COLOR = Color(0xFFE5C07B)  // Yellow

    private val KOTLIN_JAVA_KEYWORDS = setOf(
        "package", "import", "class", "interface", "object", "val", "var", "fun",
        "return", "if", "else", "when", "for", "while", "do", "try", "catch", "finally",
        "throw", "public", "private", "protected", "internal", "override", "open", "abstract",
        "sealed", "data", "enum", "companion", "new", "this", "super", "null", "true", "false",
        "static", "void", "final", "extends", "implements", "synchronized", "const", "typealias"
    )

    private val JSON_KEYWORDS = setOf("true", "false", "null")

    fun highlight(text: String, extension: String): AnnotatedString {
        if (text.length > 100_000) {
            // Guard for extremely large files to prevent UI thread lock
            return AnnotatedString(text)
        }

        return when (extension.lowercase()) {
            "kt", "java", "kts" -> highlightCode(text, KOTLIN_JAVA_KEYWORDS)
            "json" -> highlightJson(text)
            "xml", "html", "svg" -> highlightXml(text)
            else -> highlightCode(text, KOTLIN_JAVA_KEYWORDS)
        }
    }

    private fun highlightCode(text: String, keywords: Set<String>): AnnotatedString {
        val builder = buildAnnotatedString {
            append(text)

            // Highlight strings
            val stringMatcher = Pattern.compile("\"(\\\\.|[^\"])*\"").matcher(text)
            while (stringMatcher.find()) {
                addStyle(SpanStyle(color = STRING_COLOR), stringMatcher.start(), stringMatcher.end())
            }

            // Highlight line comments
            val commentMatcher = Pattern.compile("//.*").matcher(text)
            while (commentMatcher.find()) {
                addStyle(SpanStyle(color = COMMENT_COLOR), commentMatcher.start(), commentMatcher.end())
            }

            // Highlight numbers
            val numberMatcher = Pattern.compile("\\b\\d+(\\.\\d+)?([fFL])?\\b").matcher(text)
            while (numberMatcher.find()) {
                addStyle(SpanStyle(color = NUMBER_COLOR), numberMatcher.start(), numberMatcher.end())
            }

            // Highlight keywords
            val wordMatcher = Pattern.compile("\\b[a-zA-Z_][a-zA-Z0-9_]*\\b").matcher(text)
            while (wordMatcher.find()) {
                val word = wordMatcher.group()
                if (keywords.contains(word)) {
                    addStyle(
                        SpanStyle(color = KEYWORD_COLOR, fontWeight = FontWeight.Bold),
                        wordMatcher.start(),
                        wordMatcher.end()
                    )
                }
            }
        }
        return builder
    }

    private fun highlightJson(text: String): AnnotatedString {
        val builder = buildAnnotatedString {
            append(text)

            // Match keys: "key":
            val keyMatcher = Pattern.compile("\"([^\"]+)\"\\s*:").matcher(text)
            while (keyMatcher.find()) {
                addStyle(SpanStyle(color = TAG_COLOR, fontWeight = FontWeight.Bold), keyMatcher.start(1), keyMatcher.end(1))
            }

            // Match string values
            val valueMatcher = Pattern.compile(":\\s*\"(\\\\.|[^\"])*\"").matcher(text)
            while (valueMatcher.find()) {
                addStyle(SpanStyle(color = STRING_COLOR), valueMatcher.start(), valueMatcher.end())
            }

            // Match numbers
            val numberMatcher = Pattern.compile("\\b-?\\d+(\\.\\d+)?([eE][+-]?\\d+)?\\b").matcher(text)
            while (numberMatcher.find()) {
                addStyle(SpanStyle(color = NUMBER_COLOR), numberMatcher.start(), numberMatcher.end())
            }

            // Match booleans and null
            val kwMatcher = Pattern.compile("\\b(true|false|null)\\b").matcher(text)
            while (kwMatcher.find()) {
                addStyle(SpanStyle(color = KEYWORD_COLOR, fontWeight = FontWeight.Bold), kwMatcher.start(), kwMatcher.end())
            }
        }
        return builder
    }

    private fun highlightXml(text: String): AnnotatedString {
        val builder = buildAnnotatedString {
            append(text)

            // Tags: <tag or </tag>
            val tagMatcher = Pattern.compile("</?[a-zA-Z0-9_:-]+").matcher(text)
            while (tagMatcher.find()) {
                addStyle(SpanStyle(color = TAG_COLOR, fontWeight = FontWeight.Bold), tagMatcher.start(), tagMatcher.end())
            }

            // Attribute names: attr=
            val attrMatcher = Pattern.compile("\\s+([a-zA-Z0-9_:-]+)\\s*=").matcher(text)
            while (attrMatcher.find()) {
                addStyle(SpanStyle(color = ATTRIBUTE_COLOR), attrMatcher.start(1), attrMatcher.end(1))
            }

            // Attribute values: "..."
            val valMatcher = Pattern.compile("\"(\\\\.|[^\"])*\"").matcher(text)
            while (valMatcher.find()) {
                addStyle(SpanStyle(color = STRING_COLOR), valMatcher.start(), valMatcher.end())
            }

            // Comments: <!-- ... -->
            val commentMatcher = Pattern.compile("<!--[\\s\\S]*?-->").matcher(text)
            while (commentMatcher.find()) {
                addStyle(SpanStyle(color = COMMENT_COLOR), commentMatcher.start(), commentMatcher.end())
            }
        }
        return builder
    }
}
