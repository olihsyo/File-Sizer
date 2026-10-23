package com.filesizer.app.core.devtools

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
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
}
