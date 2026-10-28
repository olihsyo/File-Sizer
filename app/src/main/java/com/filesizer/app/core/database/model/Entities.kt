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

data class OperationHistoryItem(
    val id: Long = 0,
    val operation: String,
    val source: String,
    val destination: String?,
    val status: String,
    val completedCount: Int,
    val timestamp: Long = System.currentTimeMillis()
)
