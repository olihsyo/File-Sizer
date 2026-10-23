package com.filesizer.app.core.database.model

data class RecentFileItem(
    val id: Long = 0,
    val name: String,
    val path: String,
    val size: Long,
    val isDirectory: Boolean,
    val lastAccessed: Long = System.currentTimeMillis()
)

data class FavoriteItem(
    val id: Long = 0,
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val addedTimestamp: Long = System.currentTimeMillis()
)
