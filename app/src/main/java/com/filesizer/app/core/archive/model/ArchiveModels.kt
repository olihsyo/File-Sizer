package com.filesizer.app.core.archive.model

data class ArchiveEntryItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val compressedSize: Long,
    val crc: Long
)

enum class ArchiveFormat(val extension: String, val displayName: String) {
    ZIP("zip", "ZIP Archive"),
    TAR("tar", "TAR Archive"),
    GZIP("gz", "GZ Compressed File")
}
