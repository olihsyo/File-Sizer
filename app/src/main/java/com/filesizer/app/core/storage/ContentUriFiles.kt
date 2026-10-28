package com.filesizer.app.core.storage

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

data class ContentFileMetadata(
    val displayName: String,
    val size: Long,
    val mimeType: String?,
    val lastModified: Long?
)

object ContentUriFiles {
    const val MAX_TEXT_FILE_BYTES = 2L * 1024 * 1024
    const val MAX_APK_IMPORT_BYTES = 768L * 1024 * 1024
    const val MAX_ARCHIVE_IMPORT_BYTES = 1024L * 1024 * 1024

    fun metadata(context: Context, uri: Uri): ContentFileMetadata {
        var name: String? = null
        var size: Long = -1
        var modified: Long? = null
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                name = cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
            }
        }
        runCatching {
            context.contentResolver.query(uri, arrayOf("last_modified"), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex("last_modified")
                    if (index >= 0 && !cursor.isNull(index)) modified = cursor.getLong(index)
                }
            }
        }
        return ContentFileMetadata(
            displayName = name ?: uri.lastPathSegment?.substringAfterLast('/') ?: "document",
            size = size,
            mimeType = context.contentResolver.getType(uri),
            lastModified = modified
        )
    }

    suspend fun copyToCache(context: Context, uri: Uri, maxBytes: Long): Result<File> = withContext(Dispatchers.IO) {
        val operationContext = coroutineContext
        var temp: File? = null
        try {
            val metadata = metadata(context, uri)
            if (metadata.size > maxBytes) throw IllegalArgumentException("File exceeds the supported import limit (${maxBytes / (1024 * 1024)} MiB).")
            temp = File.createTempFile("filesizer-import-", ".${metadata.displayName.substringAfterLast('.', "bin")}", context.cacheDir)
            val input = context.contentResolver.openInputStream(uri) ?: throw FileNotFoundException("Cannot open the selected document.")
            var copied = 0L
            input.use { inputStream -> FileOutputStream(temp).use { outputStream ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    operationContext.ensureActive()
                    val read = inputStream.read(buffer)
                    if (read < 0) break
                    copied += read
                    if (copied > maxBytes) throw IllegalArgumentException("File exceeds the supported import limit (${maxBytes / (1024 * 1024)} MiB).")
                    outputStream.write(buffer, 0, read)
                }
                outputStream.fd.sync()
            } }
            if (metadata.size >= 0 && copied != metadata.size) throw IllegalStateException("Imported file size does not match the selected document.")
            Result.success(temp)
        } catch (cancelled: CancellationException) {
            temp?.delete()
            throw cancelled
        } catch (error: Exception) {
            temp?.delete()
            Result.failure(error)
        }
    }

    suspend fun readText(context: Context, uri: Uri, maxBytes: Long = MAX_TEXT_FILE_BYTES): Result<String> = withContext(Dispatchers.IO) {
        try {
            val meta = metadata(context, uri)
            if (meta.size > maxBytes) throw IllegalArgumentException("Text file is larger than the safe editor limit (${maxBytes / (1024 * 1024)} MiB). Open it in read-only hex view or use a smaller file.")
            val input = context.contentResolver.openInputStream(uri) ?: throw FileNotFoundException("Cannot read selected document.")
            input.use { stream ->
                val bytes = readBounded(stream, maxBytes + 1)
                if (bytes.size.toLong() > maxBytes) throw IllegalArgumentException("Text file is larger than the safe editor limit.")
                Result.success(String(bytes, Charsets.UTF_8))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    suspend fun calculateHash(context: Context, uri: Uri, algorithm: String = "SHA-256"): Result<String> = withContext(Dispatchers.IO) {
        val operationContext = coroutineContext
        try {
            val input = context.contentResolver.openInputStream(uri) ?: throw FileNotFoundException("Cannot read selected document.")
            val digest = MessageDigest.getInstance(algorithm)
            input.use { stream ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    operationContext.ensureActive()
                    val read = stream.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            Result.success(digest.digest().joinToString("") { "%02x".format(it) })
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    /** Streams an app-private output into a newly selected document and verifies the provider copy. */
    suspend fun exportFileVerified(
        context: Context,
        source: File,
        destination: Uri,
        maxBytes: Long = MAX_APK_IMPORT_BYTES
    ): Result<String> = withContext(Dispatchers.IO) {
        val operationContext = coroutineContext
        try {
            require(source.isFile && source.canRead()) { "Generated file is unavailable." }
            require(source.length() <= maxBytes) { "Generated file exceeds the supported export limit." }
            val expectedDigest = MessageDigest.getInstance("SHA-256")
            var bytesCopied = 0L
            val output = context.contentResolver.openOutputStream(destination, "w")
                ?: throw FileNotFoundException("Selected document provider does not allow writing here.")
            output.use { target ->
                source.inputStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        operationContext.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        bytesCopied += read
                        if (bytesCopied > maxBytes) throw IllegalArgumentException("Generated file exceeds the supported export limit.")
                        expectedDigest.update(buffer, 0, read)
                        target.write(buffer, 0, read)
                    }
                }
                target.flush()
                if (target is FileOutputStream) target.fd.sync()
            }
            require(bytesCopied == source.length()) { "Generated file changed while it was being exported." }

            val actualDigest = MessageDigest.getInstance("SHA-256")
            val verify = context.contentResolver.openInputStream(destination)
                ?: throw FileNotFoundException("Could not read back the exported file for verification.")
            var bytesVerified = 0L
            verify.use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    operationContext.ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    bytesVerified += read
                    if (bytesVerified > maxBytes) throw IllegalStateException("Exported file exceeds the verification limit.")
                    actualDigest.update(buffer, 0, read)
                }
            }
            val expected = expectedDigest.digest()
            val actual = actualDigest.digest()
            if (bytesVerified != bytesCopied || !MessageDigest.isEqual(expected, actual)) {
                throw IllegalStateException("Exported file did not match the generated APK.")
            }
            Result.success(actual.joinToString("") { "%02x".format(it) })
        } catch (cancelled: CancellationException) {
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, destination) }
            throw cancelled
        } catch (error: Exception) {
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, destination) }
            Result.failure(error)
        }
    }

    suspend fun writeText(context: Context, uri: Uri, content: String, maxBytes: Long = MAX_TEXT_FILE_BYTES): Result<Unit> = withContext(Dispatchers.IO) {
        var backup: File? = null
        try {
            val newBytes = content.toByteArray(Charsets.UTF_8)
            if (newBytes.size.toLong() > maxBytes) throw IllegalArgumentException("Edited text exceeds the safe save limit.")
            val oldMeta = metadata(context, uri)
            if (oldMeta.size > maxBytes) throw IllegalArgumentException("This document is larger than the safe overwrite limit.")
            val existing = context.contentResolver.openInputStream(uri) ?: throw FileNotFoundException("Cannot read document before saving.")
            backup = File.createTempFile("filesizer-backup-", ".tmp", context.cacheDir)
            existing.use { input -> FileOutputStream(backup).use { output -> input.copyTo(output); output.fd.sync() } }

            val output = context.contentResolver.openOutputStream(uri, "wt") ?: throw IllegalStateException("Selected provider does not allow writing this document.")
            output.use { stream -> stream.write(newBytes); stream.flush() }

            val verify = context.contentResolver.openInputStream(uri) ?: throw FileNotFoundException("Cannot verify saved document.")
            val savedBytes = verify.use { readBounded(it, maxBytes + 1) }
            if (!savedBytes.contentEquals(newBytes)) throw IllegalStateException("Saved document did not match the edited content.")
            Result.success(Unit)
        } catch (cancelled: CancellationException) {
            backup?.let { restore(context, uri, it) }
            throw cancelled
        } catch (error: Exception) {
            backup?.let { restore(context, uri, it) }
            Result.failure(error)
        } finally {
            backup?.delete()
        }
    }

    private fun restore(context: Context, uri: Uri, backup: File) {
        runCatching {
            val output = context.contentResolver.openOutputStream(uri, "wt") ?: return
            output.use { stream -> backup.inputStream().use { it.copyTo(stream); stream.flush() } }
        }
    }

    private fun readBounded(input: java.io.InputStream, limit: Long): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > limit) break
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }
}
