package com.filesizer.app.core.operations

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID
import kotlin.coroutines.coroutineContext

/** Progress is reported as completed and estimated bytes. The total can change if files change mid-operation. */
data class FileOperationProgress(val completedBytes: Long, val totalBytes: Long)

open class PartialOperationException(
    message: String,
    val completedItems: Int,
    cause: Throwable? = null
) : Exception(message, cause)

class PartialMoveException(message: String, completedItems: Int = 0, cause: Throwable? = null) :
    PartialOperationException(message, completedItems, cause)

object FileOperationService {

    suspend fun createFolder(parent: File, folderName: String): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val name = validateName(folderName)
            if (!parent.exists() || !parent.isDirectory || !parent.canWrite()) {
                throw IllegalStateException("The parent folder is unavailable or not writable.")
            }
            File(parent, name).also {
                if (!it.mkdir()) throw IllegalStateException("Could not create folder; it may already exist or access was denied.")
            }
        }
    }

    suspend fun renameItem(target: File, newName: String): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val name = validateName(newName)
            if (!target.exists() || !target.canWrite()) throw IllegalStateException("The item is unavailable or cannot be renamed.")
            val parent = target.parentFile ?: throw IllegalArgumentException("The item has no parent folder.")
            val destination = File(parent, name)
            if (destination.exists()) throw IllegalStateException("Destination already exists.")
            if (!target.renameTo(destination)) throw IllegalStateException("Rename failed. Check storage access and try again.")
            destination
        }
    }

    suspend fun deleteItems(targets: List<File>): Result<Int> = withContext(Dispatchers.IO) {
        var deleted = 0
        try {
            targets.forEach { target ->
                coroutineContext.ensureActive()
                if (!target.exists()) throw IllegalStateException("Item no longer exists: ${target.name}")
                val success = if (target.isDirectory) target.deleteRecursively() else target.delete()
                if (!success) throw IllegalStateException("Could not delete ${target.name}; access may have been denied.")
                deleted++
            }
            Result.success(deleted)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (deleted > 0) Result.failure(PartialOperationException("Deleted $deleted item(s) before the next item failed: ${error.message}", deleted, error))
            else Result.failure(error)
        }
    }

    suspend fun calculateSha256(file: File): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!file.isFile || !file.canRead()) throw IllegalArgumentException("Target must be a readable file.")
            Result.success(hashFile(file))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    suspend fun copyItems(
        sources: List<File>,
        targetDir: File,
        policy: ConflictPolicy = ConflictPolicy.RENAME_NEW,
        onProgress: ((FileOperationProgress) -> Unit)? = null
    ): Result<Int> = runBatch(sources, targetDir, policy, move = false, onProgress = onProgress)

    suspend fun moveItems(
        sources: List<File>,
        targetDir: File,
        policy: ConflictPolicy = ConflictPolicy.RENAME_NEW,
        onProgress: ((FileOperationProgress) -> Unit)? = null
    ): Result<Int> = runBatch(sources, targetDir, policy, move = true, onProgress = onProgress)

    private suspend fun runBatch(
        sources: List<File>,
        targetDir: File,
        policy: ConflictPolicy,
        move: Boolean,
        onProgress: ((FileOperationProgress) -> Unit)?
    ): Result<Int> = withContext(Dispatchers.IO) {
        var completedItems = 0
        try {
            if (sources.isEmpty()) return@withContext Result.success(0)
            if (!targetDir.exists() && !targetDir.mkdirs()) throw IllegalStateException("Could not create destination folder.")
            if (!targetDir.isDirectory || !targetDir.canWrite()) throw IllegalStateException("Destination folder is not writable.")
            val totalBytes = sources.sumOf { treeSize(it) }
            var completedBytes = 0L

            for (source in sources) {
                coroutineContext.ensureActive()
                if (!source.exists() || !source.canRead()) throw IllegalStateException("Source is unavailable or unreadable: ${source.name}")
                if (source.canonicalFile == targetDir.canonicalFile ||
                    (source.isDirectory && targetDir.canonicalPath.startsWith(source.canonicalPath + File.separator))) {
                    throw IllegalArgumentException("A folder cannot be copied or moved into itself or one of its children.")
                }
                val destination = resolveDestination(source, targetDir, policy) ?: continue

                if (move && !destination.exists() && source.renameTo(destination)) {
                    completedBytes += treeSize(destination)
                    completedItems++
                    onProgress?.invoke(FileOperationProgress(completedBytes, totalBytes))
                    continue
                }

                val before = completedBytes
                try {
                    copyAtomically(source, destination, replace = destination.exists() && policy == ConflictPolicy.REPLACE) { copied ->
                        onProgress?.invoke(FileOperationProgress(before + copied, totalBytes))
                    }
                    val copiedSize = treeSize(destination)
                    if (copiedSize != treeSize(source)) throw IllegalStateException("Copied output size does not match the source.")
                    completedBytes += copiedSize
                    if (move) {
                        if (!source.deleteRecursively()) {
                            throw PartialMoveException("Copied ${source.name}, but could not remove the original. Both copies were kept.")
                        }
                    }
                    completedItems++
                    onProgress?.invoke(FileOperationProgress(completedBytes, totalBytes))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                }
            }
            Result.success(completedItems)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (completedItems > 0 && error !is PartialOperationException) {
                Result.failure(PartialOperationException("Completed $completedItems item(s) before the next item failed: ${error.message}", completedItems, error))
            } else Result.failure(error)
        }
    }

    private fun resolveDestination(source: File, targetDir: File, policy: ConflictPolicy): File? {
        val destination = File(targetDir, source.name)
        if (!destination.exists()) return destination
        return when (policy) {
            ConflictPolicy.SKIP -> null
            ConflictPolicy.REPLACE -> destination
            ConflictPolicy.RENAME_NEW -> {
                val base = source.nameWithoutExtension
                val extension = source.extension.takeIf { it.isNotEmpty() }?.let { ".$it" }.orEmpty()
                var index = 1
                var candidate: File
                do {
                    candidate = File(targetDir, "$base ($index)$extension")
                    index++
                } while (candidate.exists())
                candidate
            }
        }
    }

    private suspend fun copyAtomically(
        source: File,
        destination: File,
        replace: Boolean,
        onProgress: (Long) -> Unit
    ) {
        val parent = destination.parentFile ?: throw IllegalArgumentException("Destination has no parent folder.")
        val stage = File(parent, ".${destination.name}.${UUID.randomUUID()}.partial")
        var backup: File? = null
        try {
            if (source.isDirectory) {
                if (!stage.mkdir()) throw IllegalStateException("Could not create temporary destination folder.")
                copyDirectoryContents(source, stage, 0L, onProgress)
                if (!sameTreeShapeAndSizes(source, stage)) throw IllegalStateException("Copied folder did not match the source.")
            } else {
                copyVerifiedFile(source, stage, 0L, onProgress)
            }

            if (destination.exists()) {
                if (!replace) throw IllegalStateException("Destination already exists: ${destination.name}")
                backup = File(parent, ".${destination.name}.${UUID.randomUUID()}.backup")
                if (!destination.renameTo(backup)) throw IllegalStateException("Could not safely stage the existing destination.")
            }
            if (!stage.renameTo(destination)) {
                backup?.renameTo(destination)
                throw IllegalStateException("Could not commit the completed copy.")
            }
            backup?.let { if (it.isDirectory) it.deleteRecursively() else it.delete() }
        } finally {
            if (stage.exists()) {
                if (stage.isDirectory) stage.deleteRecursively() else stage.delete()
            }
            backup?.takeIf { it.exists() && !destination.exists() }?.renameTo(destination)
        }
    }

    private suspend fun copyDirectoryContents(source: File, target: File, priorBytes: Long, onProgress: (Long) -> Unit): Long {
        coroutineContext.ensureActive()
        val children = source.listFiles() ?: throw IllegalStateException("Could not read folder: ${source.name}")
        var copied = priorBytes
        for (child in children) {
            coroutineContext.ensureActive()
            val output = File(target, child.name)
            if (child.isDirectory) {
                if (!output.mkdir()) throw IllegalStateException("Could not create folder: ${output.name}")
                copied = copyDirectoryContents(child, output, copied, onProgress)
            } else {
                copied = copyVerifiedFile(child, output, copied, onProgress)
            }
        }
        return copied
    }

    private suspend fun copyVerifiedFile(source: File, target: File, priorBytes: Long, onProgress: (Long) -> Unit): Long {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var copied = 0L
        FileInputStream(source).use { input ->
            FileOutputStream(target).use { output ->
                while (true) {
                    coroutineContext.ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    digest.update(buffer, 0, read)
                    copied += read
                    onProgress(priorBytes + copied)
                }
                output.fd.sync()
            }
        }
        if (copied != source.length() || !target.isFile || target.length() != copied) {
            throw IllegalStateException("Copy size verification failed for ${source.name}.")
        }
        if (!MessageDigest.isEqual(digest.digest(), hashFileBytes(target))) {
            throw IllegalStateException("Copy content verification failed for ${source.name}.")
        }
        return priorBytes + copied
    }

    private fun sameTreeShapeAndSizes(source: File, target: File): Boolean {
        fun manifest(root: File): Map<String, Long>? {
            val result = linkedMapOf<String, Long>()
            fun visit(node: File, relative: String): Boolean {
                if (node.isDirectory) {
                    val children = node.listFiles() ?: return false
                    children.forEach { child ->
                        val path = if (relative.isEmpty()) child.name else "$relative/${child.name}"
                        if (!visit(child, path)) return false
                    }
                } else result[relative] = node.length()
                return true
            }
            return if (visit(root, "")) result else null
        }
        return manifest(source) == manifest(target)
    }

    private fun treeSize(file: File): Long = when {
        !file.exists() -> 0L
        file.isFile -> file.length()
        else -> file.listFiles()?.sumOf(::treeSize) ?: throw IllegalStateException("Could not read folder: ${file.name}")
    }

    private suspend fun hashFile(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                coroutineContext.ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private suspend fun hashFileBytes(file: File): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                coroutineContext.ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest()
    }

    private fun validateName(raw: String): String {
        val name = raw.trim()
        if (name.isEmpty() || name == "." || name == ".." || name.contains('/') || name.contains('\\') || name.contains('\u0000')) {
            throw IllegalArgumentException("Enter a valid single file or folder name.")
        }
        return name
    }
}
