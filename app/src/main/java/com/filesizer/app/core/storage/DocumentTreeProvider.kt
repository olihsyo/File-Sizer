package com.filesizer.app.core.storage

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.security.MessageDigest
import java.util.UUID
import kotlin.coroutines.coroutineContext

data class StorageDocument(
    val uri: Uri,
    val name: String,
    val mimeType: String,
    val size: Long,
    val lastModified: Long,
    val canRead: Boolean,
    val canWrite: Boolean
) {
    val isDirectory: Boolean get() = mimeType == DocumentsContract.Document.MIME_TYPE_DIR
}

data class DocumentOperationProgress(val completedBytes: Long, val totalBytes: Long)

class DocumentPartialMoveException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** SAF provider. It uses content URIs directly and never attempts to turn document IDs into file paths. */
object DocumentTreeProvider {
    private val childProjection = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_SIZE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        DocumentsContract.Document.COLUMN_FLAGS
    )

    suspend fun listChildren(context: Context, directoryUri: Uri): Result<List<StorageDocument>> = withContext(Dispatchers.IO) {
        val operationContext = coroutineContext
        try {
            val resolver = context.contentResolver
            val documentId = DocumentsContract.getDocumentId(directoryUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(directoryUri, documentId)
            val documents = mutableListOf<StorageDocument>()
            resolver.query(childrenUri, childProjection, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    operationContext.ensureActive()
                    val id = cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID))
                    val name = cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)) ?: "Unnamed"
                    val mime = cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE))
                        ?: "application/octet-stream"
                    val sizeColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                    val modifiedColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                    val flagsColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_FLAGS)
                    val flags = if (flagsColumn >= 0) cursor.getInt(flagsColumn) else 0
                    documents += StorageDocument(
                        uri = DocumentsContract.buildDocumentUriUsingTree(directoryUri, id),
                        name = name,
                        mimeType = mime,
                        size = if (sizeColumn >= 0 && !cursor.isNull(sizeColumn)) cursor.getLong(sizeColumn) else -1L,
                        lastModified = if (modifiedColumn >= 0 && !cursor.isNull(modifiedColumn)) cursor.getLong(modifiedColumn) else 0L,
                        canRead = true,
                        canWrite = flags and DocumentsContract.Document.FLAG_SUPPORTS_WRITE != 0
                    )
                }
            } ?: throw FileNotFoundException("The selected storage provider did not return a directory listing.")
            Result.success(documents)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    suspend fun getDocument(context: Context, uri: Uri): Result<StorageDocument> = withContext(Dispatchers.IO) {
        try {
            val result = context.contentResolver.query(uri, childProjection, null, null, null)
                ?: throw FileNotFoundException("Storage provider did not return file metadata.")
            result.use { cursor ->
                if (!cursor.moveToFirst()) throw FileNotFoundException("Selected document is no longer available.")
                val name = cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)) ?: "Unnamed"
                val mime = cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE))
                    ?: "application/octet-stream"
                val sizeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                val modifiedIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                val flagsIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_FLAGS)
                val flags = if (flagsIndex >= 0) cursor.getInt(flagsIndex) else 0
                Result.success(
                    StorageDocument(
                        uri = uri,
                        name = name,
                        mimeType = mime,
                        size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else -1L,
                        lastModified = if (modifiedIndex >= 0 && !cursor.isNull(modifiedIndex)) cursor.getLong(modifiedIndex) else 0L,
                        canRead = true,
                        canWrite = flags and DocumentsContract.Document.FLAG_SUPPORTS_WRITE != 0
                    )
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    suspend fun createFolder(context: Context, parentUri: Uri, name: String): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            validateName(name)
            val resolver = context.contentResolver
            if (listChildren(context, parentUri).getOrThrow().any { it.name == name }) {
                throw IllegalStateException("A file or folder with this name already exists.")
            }
            Result.success(DocumentsContract.createDocument(resolver, parentUri, DocumentsContract.Document.MIME_TYPE_DIR, name)
                ?: throw IllegalStateException("Storage provider could not create the folder."))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    suspend fun rename(context: Context, uri: Uri, newName: String): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            validateName(newName)
            Result.success(DocumentsContract.renameDocument(context.contentResolver, uri, newName)
                ?: throw IllegalStateException("Storage provider does not support renaming this document."))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    suspend fun delete(context: Context, uris: List<Uri>): Result<Int> = withContext(Dispatchers.IO) {
        try {
            var count = 0
            for (uri in uris) {
                coroutineContext.ensureActive()
                if (!DocumentsContract.deleteDocument(context.contentResolver, uri)) {
                    throw IllegalStateException("Storage provider could not delete the selected item.")
                }
                count++
            }
            Result.success(count)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    suspend fun copyOrMove(
        context: Context,
        sources: List<Uri>,
        destinationUri: Uri,
        move: Boolean,
        renameOnConflict: Boolean = true,
        onProgress: ((DocumentOperationProgress) -> Unit)? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        val operationContext = coroutineContext
        val completedDestinations = mutableListOf<Uri>()
        try {
            val resolver = context.contentResolver
            val destinationChildren = listChildren(context, destinationUri).getOrThrow()
            val existingNames = destinationChildren.map { it.name }.toMutableSet()
            val totalBytes = sources.sumOf { uri -> getDocument(context, uri).getOrThrow().size.coerceAtLeast(0L) }
            var completedBytes = 0L

            for (sourceUri in sources) {
                operationContext.ensureActive()
                val source = getDocument(context, sourceUri).getOrThrow()
                if (source.isDirectory && isDescendantOrSame(context, destinationUri, sourceUri)) {
                    throw IllegalArgumentException("A folder cannot be copied into itself or one of its children.")
                }
                var finalName = source.name
                if (finalName in existingNames) {
                    if (!renameOnConflict) continue
                    val dot = finalName.lastIndexOf('.')
                    val base = if (dot > 0) finalName.substring(0, dot) else finalName
                    val ext = if (dot > 0) finalName.substring(dot) else ""
                    var index = 1
                    while ("$base ($index)$ext" in existingNames) index++
                    finalName = "$base ($index)$ext"
                }
                val temporaryName = ".$finalName.${UUID.randomUUID()}.partial"
                val stagedUri = DocumentsContract.createDocument(resolver, destinationUri, source.mimeType, temporaryName)
                    ?: throw IllegalStateException("Storage provider could not stage ${source.name}.")
                try {
                    val digest = MessageDigest.getInstance("SHA-256")
                    var copied = 0L
                    if (source.isDirectory) {
                        copied = copyDirectoryContents(context, sourceUri, stagedUri, digest, copied, totalBytes, operationContext, onProgress)
                        if (!sameDirectoryContents(context, sourceUri, stagedUri)) throw IllegalStateException("Copied folder did not match the source.")
                    } else {
                        val progress = copyStream(resolver, sourceUri, stagedUri, operationContext, totalBytes - completedBytes, completedBytes, onProgress)
                        copied = progress.first
                        if (source.size >= 0 && copied != source.size) throw IllegalStateException("Copied file size did not match the source.")
                        val stagedHash = hashUri(resolver, stagedUri, operationContext)
                        if (!MessageDigest.isEqual(progress.second, stagedHash)) throw IllegalStateException("Copied file content did not match the source.")
                    }
                    val finalUri = DocumentsContract.renameDocument(resolver, stagedUri, finalName)
                        ?: throw IllegalStateException("Storage provider could not finalize ${source.name}.")
                    completedDestinations += finalUri
                    existingNames += finalName
                    completedBytes += copied
                    if (move && !DocumentsContract.deleteDocument(resolver, sourceUri)) {
                        throw DocumentPartialMoveException("Copied ${source.name}, but could not remove the original. Both copies were kept.")
                    }
                    onProgress?.invoke(DocumentOperationProgress(completedBytes, totalBytes))
                } catch (error: Exception) {
                    runCatching { DocumentsContract.deleteDocument(resolver, stagedUri) }
                    if (error is CancellationException) throw error
                    throw error
                }
            }
            Result.success(completedDestinations.size)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private suspend fun copyDirectoryContents(
        context: Context,
        sourceDirectory: Uri,
        targetDirectory: Uri,
        digest: MessageDigest,
        priorBytes: Long,
        totalBytes: Long,
        operationContext: kotlin.coroutines.CoroutineContext,
        onProgress: ((DocumentOperationProgress) -> Unit)?
    ): Long {
        var completed = priorBytes
        val children = listChildren(context, sourceDirectory).getOrThrow()
        for (child in children) {
            operationContext.ensureActive()
            val targetChild = DocumentsContract.createDocument(
                context.contentResolver,
                targetDirectory,
                child.mimeType,
                child.name
            ) ?: throw IllegalStateException("Storage provider could not create ${child.name}.")
            if (child.isDirectory) {
                completed = copyDirectoryContents(context, child.uri, targetChild, digest, completed, totalBytes, operationContext, onProgress)
            } else {
                val progress = copyStream(context.contentResolver, child.uri, targetChild, operationContext, totalBytes - completed, completed, onProgress)
                completed += progress.first
                if (child.size >= 0 && progress.first != child.size) throw IllegalStateException("Copied file size did not match ${child.name}.")
                if (!MessageDigest.isEqual(progress.second, hashUri(context.contentResolver, targetChild, operationContext))) {
                    throw IllegalStateException("Copied file content did not match ${child.name}.")
                }
            }
        }
        return completed
    }

    private suspend fun copyStream(
        resolver: ContentResolver,
        source: Uri,
        target: Uri,
        operationContext: kotlin.coroutines.CoroutineContext,
        totalBytes: Long,
        completedBefore: Long,
        onProgress: ((DocumentOperationProgress) -> Unit)?
    ): Pair<Long, ByteArray> {
        val digest = MessageDigest.getInstance("SHA-256")
        var bytes = 0L
        val input = resolver.openInputStream(source) ?: throw FileNotFoundException("Cannot open source document.")
        val output = resolver.openOutputStream(target, "w") ?: throw IllegalStateException("Cannot write destination document.")
        input.use { inputStream -> output.use { outputStream ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                operationContext.ensureActive()
                val read = inputStream.read(buffer)
                if (read < 0) break
                outputStream.write(buffer, 0, read)
                digest.update(buffer, 0, read)
                bytes += read
                onProgress?.invoke(DocumentOperationProgress(completedBefore + bytes, completedBefore + totalBytes))
            }
            outputStream.flush()
        } }
        return bytes to digest.digest()
    }

    private suspend fun hashUri(resolver: ContentResolver, uri: Uri, operationContext: kotlin.coroutines.CoroutineContext): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        val input = resolver.openInputStream(uri) ?: throw FileNotFoundException("Cannot read staged document.")
        input.use { stream ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                operationContext.ensureActive()
                val read = stream.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest()
    }

    private suspend fun sameDirectoryContents(context: Context, source: Uri, target: Uri): Boolean {
        val sourceChildren = listChildren(context, source).getOrThrow().associateBy { it.name }
        val targetChildren = listChildren(context, target).getOrThrow().associateBy { it.name }
        if (sourceChildren.keys != targetChildren.keys) return false
        for ((name, entry) in sourceChildren) {
            val copied = targetChildren.getValue(name)
            if (entry.isDirectory != copied.isDirectory || (!entry.isDirectory && entry.size >= 0 && entry.size != copied.size)) return false
            if (entry.isDirectory && !sameDirectoryContents(context, entry.uri, copied.uri)) return false
        }
        return true
    }

    private suspend fun isDescendantOrSame(context: Context, destination: Uri, source: Uri): Boolean {
        val destinationId = runCatching { DocumentsContract.getDocumentId(destination) }.getOrNull() ?: return false
        if (runCatching { DocumentsContract.getDocumentId(source) }.getOrNull() == destinationId) return true
        var pending = listOf(source)
        var visited = 0
        while (pending.isNotEmpty() && visited < 10_000) {
            val current = pending.first()
            pending = pending.drop(1)
            val children = listChildren(context, current).getOrNull().orEmpty()
            if (children.any { runCatching { DocumentsContract.getDocumentId(it.uri) }.getOrNull() == destinationId }) return true
            pending = pending + children.filter { it.isDirectory }.map { it.uri }
            visited += children.size
        }
        return false
    }

    private fun validateName(name: String) {
        if (name.isBlank() || name == "." || name == ".." || name.contains('/') || name.contains('\\') || name.contains('\u0000')) {
            throw IllegalArgumentException("Enter a valid single file or folder name.")
        }
    }
}
