package com.filesizer.app.core.operations

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest

object FileOperationService {

    suspend fun createFolder(parent: File, folderName: String): Result<File> = withContext(Dispatchers.IO) {
        try {
            val sanitized = folderName.trim().replace("/", "").replace("\\", "")
            if (sanitized.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("Folder name cannot be empty."))
            }
            val newDir = File(parent, sanitized)
            if (newDir.exists()) {
                return@withContext Result.failure(IllegalStateException("A folder or file with this name already exists."))
            }
            if (newDir.mkdirs()) {
                Result.success(newDir)
            } else {
                Result.failure(Exception("Failed to create folder. Check storage permissions."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun renameItem(target: File, newName: String): Result<File> = withContext(Dispatchers.IO) {
        try {
            val sanitized = newName.trim().replace("/", "").replace("\\", "")
            if (sanitized.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("New name cannot be empty."))
            }
            val destination = File(target.parentFile, sanitized)
            if (destination.exists()) {
                return@withContext Result.failure(IllegalStateException("Destination file already exists."))
            }
            if (target.renameTo(destination)) {
                Result.success(destination)
            } else {
                Result.failure(Exception("Failed to rename item."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteItems(targets: List<File>): Result<Int> = withContext(Dispatchers.IO) {
        try {
            var count = 0
            for (file in targets) {
                if (file.isDirectory) {
                    if (file.deleteRecursively()) count++
                } else {
                    if (file.delete()) count++
                }
            }
            Result.success(count)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun calculateSha256(file: File): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!file.exists() || file.isDirectory) {
                return@withContext Result.failure(IllegalArgumentException("Target must be a readable file."))
            }
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(8192)
            FileInputStream(file).use { fis ->
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            val hexString = digest.digest().joinToString("") { "%02x".format(it) }
            Result.success(hexString)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun copyItems(
        sources: List<File>,
        targetDir: File,
        policy: ConflictPolicy = ConflictPolicy.RENAME_NEW
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }
            var count = 0
            for (src in sources) {
                val destFile = resolveDestinationFile(src, targetDir, policy) ?: continue
                if (src.isDirectory) {
                    copyDirectory(src, destFile)
                } else {
                    copySingleFile(src, destFile)
                }
                count++
            }
            Result.success(count)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun moveItems(
        sources: List<File>,
        targetDir: File,
        policy: ConflictPolicy = ConflictPolicy.RENAME_NEW
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }
            var count = 0
            for (src in sources) {
                val destFile = resolveDestinationFile(src, targetDir, policy) ?: continue
                if (src.renameTo(destFile)) {
                    count++
                } else {
                    // Fallback to copy and verified delete
                    if (src.isDirectory) {
                        copyDirectory(src, destFile)
                    } else {
                        copySingleFile(src, destFile)
                    }
                    if (src.deleteRecursively()) {
                        count++
                    }
                }
            }
            Result.success(count)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun resolveDestinationFile(source: File, targetDir: File, policy: ConflictPolicy): File? {
        val dest = File(targetDir, source.name)
        if (!dest.exists()) return dest

        return when (policy) {
            ConflictPolicy.REPLACE -> {
                dest.deleteRecursively()
                dest
            }
            ConflictPolicy.SKIP -> null
            ConflictPolicy.RENAME_NEW -> {
                val base = source.nameWithoutExtension
                val ext = if (source.extension.isNotEmpty()) ".${source.extension}" else ""
                var counter = 1
                var candidate: File
                do {
                    candidate = File(targetDir, "$base ($counter)$ext")
                    counter++
                } while (candidate.exists())
                candidate
            }
        }
    }

    private fun copySingleFile(source: File, target: File) {
        FileInputStream(source).use { inStream ->
            FileOutputStream(target).use { outStream ->
                val buffer = ByteArray(8192)
                var length: Int
                while (inStream.read(buffer).also { length = it } > 0) {
                    outStream.write(buffer, 0, length)
                }
            }
        }
    }

    private fun copyDirectory(sourceDir: File, targetDir: File) {
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }
        val children = sourceDir.listFiles() ?: return
        for (child in children) {
            val destChild = File(targetDir, child.name)
            if (child.isDirectory) {
                copyDirectory(child, destChild)
            } else {
                copySingleFile(child, destChild)
            }
        }
    }
}
