package com.filesizer.app.core.devtools

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.io.StringReader
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.xml.sax.InputSource
import java.util.Base64

object DevToolsService {

    fun calculateHash(input: String, algorithm: String): String {
        return try {
            val digest = MessageDigest.getInstance(algorithm)
            val bytes = digest.digest(input.toByteArray(StandardCharsets.UTF_8))
            bytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    fun base64Encode(input: String): String {
        return try {
            Base64.getEncoder().encodeToString(input.toByteArray(StandardCharsets.UTF_8))
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    fun base64Decode(input: String): String {
        return try {
            val bytes = Base64.getDecoder().decode(input.trim())
            String(bytes, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            "Error: Invalid Base64 string"
        }
    }

    fun urlEncode(input: String): String {
        return try {
            URLEncoder.encode(input, StandardCharsets.UTF_8.name())
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    fun urlDecode(input: String): String {
        return try {
            URLDecoder.decode(input, StandardCharsets.UTF_8.name())
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    fun formatJson(input: String): Result<String> {
        return try {
            val trimmed = input.trim()
            if (trimmed.startsWith("{")) {
                val json = JSONObject(trimmed)
                Result.success(json.toString(2))
            } else if (trimmed.startsWith("[")) {
                val json = JSONArray(trimmed)
                Result.success(json.toString(2))
            } else {
                Result.failure(IllegalArgumentException("Input does not start with { or [."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun formatXml(input: String): Result<String> {
        return try {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                isXIncludeAware = false
                setExpandEntityReferences(false)
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "")
                setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "")
            }
            val document = factory.newDocumentBuilder().parse(InputSource(StringReader(input)))
            val transformerFactory = TransformerFactory.newInstance().apply {
                setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "")
                setAttribute("http://javax.xml.XMLConstants/property/accessExternalStylesheet", "")
            }
            val transformer = transformerFactory.newTransformer().apply {
                setOutputProperty(OutputKeys.INDENT, "yes")
                setOutputProperty(OutputKeys.ENCODING, "UTF-8")
                setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")
            }
            val output = StringWriter()
            transformer.transform(DOMSource(document), StreamResult(output))
            Result.success(output.toString())
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    /** A deliberately simple line diff; it reports changed lines but does not claim optimal edit-distance alignment. */
    fun diffText(before: String, after: String): String {
        val left = before.lines()
        val right = after.lines()
        val max = maxOf(left.size, right.size)
        return buildString {
            for (index in 0 until max) {
                val old = left.getOrNull(index)
                val new = right.getOrNull(index)
                if (old == new) append("  ").append(index + 1).append("  ").append(old.orEmpty()).append('\n')
                else {
                    if (old != null) append("- ").append(index + 1).append("  ").append(old).append('\n')
                    if (new != null) append("+ ").append(index + 1).append("  ").append(new).append('\n')
                }
            }
        }.trimEnd()
    }
}
