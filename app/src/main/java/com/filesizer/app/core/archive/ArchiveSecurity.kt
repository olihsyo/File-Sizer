package com.filesizer.app.core.archive

import java.io.File

object ArchiveSecurity {

    /**
     * Validates that an archive entry name does not escape the destination directory.
     * Prevents Zip Slip vulnerabilities.
     */
    @Throws(SecurityException::class)
    fun resolveAndValidateEntry(destinationDir: File, entryName: String): File {
        val destCanonical = destinationDir.canonicalPath
        // Normalize slashes
        val normalizedName = entryName.replace('\\', '/')
        val targetFile = File(destinationDir, normalizedName)
        val targetCanonical = targetFile.canonicalPath

        if (!targetCanonical.startsWith(destCanonical + File.separator) && targetCanonical != destCanonical) {
            throw SecurityException("Zip Slip path traversal vulnerability detected! Entry: $entryName")
        }

        return targetFile
    }
}
