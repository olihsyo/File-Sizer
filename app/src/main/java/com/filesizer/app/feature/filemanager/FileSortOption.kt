package com.filesizer.app.feature.filemanager

enum class SortField(val label: String) {
    NAME("Name"),
    SIZE("Size"),
    DATE("Date"),
    TYPE("Type")
}

data class FileSortOption(
    val field: SortField = SortField.NAME,
    val ascending: Boolean = true
)

enum class FileFilterType(val label: String) {
    ALL("All"),
    IMAGES("Images"),
    VIDEOS("Videos"),
    AUDIO("Audio"),
    DOCUMENTS("Docs"),
    ARCHIVES("Archives"),
    APKS("APKs")
}
