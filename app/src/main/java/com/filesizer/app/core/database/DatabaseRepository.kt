package com.filesizer.app.core.database

import android.content.Context
import com.filesizer.app.core.database.model.FavoriteItem
import com.filesizer.app.core.database.model.RecentFileItem
import com.filesizer.app.core.database.model.OperationHistoryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class DatabaseRepository(context: Context) {

    private val dbHelper = FileSizerDbHelper(context)

    private val _recentFilesFlow = MutableStateFlow<List<RecentFileItem>>(emptyList())
    val recentFilesFlow: Flow<List<RecentFileItem>> = _recentFilesFlow.asStateFlow()

    private val _favoritesFlow = MutableStateFlow<List<FavoriteItem>>(emptyList())
    val favoritesFlow: Flow<List<FavoriteItem>> = _favoritesFlow.asStateFlow()

    private val _operationHistoryFlow = MutableStateFlow<List<OperationHistoryItem>>(emptyList())
    val operationHistoryFlow: Flow<List<OperationHistoryItem>> = _operationHistoryFlow.asStateFlow()

    suspend fun refresh() = withContext(Dispatchers.IO) {
        _recentFilesFlow.value = dbHelper.getRecentFiles()
        _favoritesFlow.value = dbHelper.getFavorites()
        _operationHistoryFlow.value = dbHelper.getOperationHistory()
    }

    suspend fun recordRecentAccess(name: String, path: String, size: Long, isDirectory: Boolean) = withContext(Dispatchers.IO) {
        val item = RecentFileItem(
            name = name,
            path = path,
            size = size,
            isDirectory = isDirectory
        )
        dbHelper.insertOrUpdateRecent(item)
        _recentFilesFlow.value = dbHelper.getRecentFiles()
    }

    suspend fun toggleFavorite(name: String, path: String, isDirectory: Boolean): Boolean = withContext(Dispatchers.IO) {
        val currentlyFav = dbHelper.isFavorite(path)
        if (currentlyFav) {
            dbHelper.removeFavorite(path)
        } else {
            dbHelper.addFavorite(FavoriteItem(name = name, path = path, isDirectory = isDirectory))
        }
        _favoritesFlow.value = dbHelper.getFavorites()
        !currentlyFav
    }

    suspend fun isFavorite(path: String): Boolean = withContext(Dispatchers.IO) {
        dbHelper.isFavorite(path)
    }

    suspend fun recordOperation(item: OperationHistoryItem) = withContext(Dispatchers.IO) {
        dbHelper.recordOperation(item)
        _operationHistoryFlow.value = dbHelper.getOperationHistory()
    }

    suspend fun removeFavorite(path: String) = withContext(Dispatchers.IO) {
        dbHelper.removeFavorite(path)
        _favoritesFlow.value = dbHelper.getFavorites()
    }
}
