package com.filesizer.app.core.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.filesizer.app.core.database.model.FavoriteItem
import com.filesizer.app.core.database.model.RecentFileItem
import com.filesizer.app.core.database.model.OperationHistoryItem

class FileSizerDbHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "filesizer.db"
        const val DATABASE_VERSION = 2

        // Table Recent Files
        const val TABLE_RECENTS = "recent_files"
        const val COL_RECENT_ID = "id"
        const val COL_RECENT_NAME = "name"
        const val COL_RECENT_PATH = "path"
        const val COL_RECENT_SIZE = "size"
        const val COL_RECENT_IS_DIR = "is_directory"
        const val COL_RECENT_ACCESSED = "last_accessed"

        // Table Favorites
        const val TABLE_FAVORITES = "favorites"
        const val COL_FAV_ID = "id"
        const val COL_FAV_NAME = "name"
        const val COL_FAV_PATH = "path"
        const val COL_FAV_IS_DIR = "is_directory"
        const val COL_FAV_ADDED = "added_timestamp"

        const val TABLE_OPERATIONS = "operation_history"
        const val COL_OPERATION_ID = "id"
        const val COL_OPERATION_NAME = "operation"
        const val COL_OPERATION_SOURCE = "source"
        const val COL_OPERATION_DESTINATION = "destination"
        const val COL_OPERATION_STATUS = "status"
        const val COL_OPERATION_COUNT = "completed_count"
        const val COL_OPERATION_TIMESTAMP = "timestamp"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createRecentsQuery = """
            CREATE TABLE $TABLE_RECENTS (
                $COL_RECENT_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_RECENT_NAME TEXT NOT NULL,
                $COL_RECENT_PATH TEXT UNIQUE NOT NULL,
                $COL_RECENT_SIZE INTEGER NOT NULL,
                $COL_RECENT_IS_DIR INTEGER NOT NULL,
                $COL_RECENT_ACCESSED INTEGER NOT NULL
            )
        """.trimIndent()

        val createFavoritesQuery = """
            CREATE TABLE $TABLE_FAVORITES (
                $COL_FAV_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_FAV_NAME TEXT NOT NULL,
                $COL_FAV_PATH TEXT UNIQUE NOT NULL,
                $COL_FAV_IS_DIR INTEGER NOT NULL,
                $COL_FAV_ADDED INTEGER NOT NULL
            )
        """.trimIndent()

        db.execSQL(createRecentsQuery)
        db.execSQL(createFavoritesQuery)
        db.execSQL("CREATE INDEX idx_recents_path ON $TABLE_RECENTS ($COL_RECENT_PATH)")
        db.execSQL("CREATE INDEX idx_favorites_path ON $TABLE_FAVORITES ($COL_FAV_PATH)")
        createOperationHistoryTable(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) createOperationHistoryTable(db)
    }

    private fun createOperationHistoryTable(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE_OPERATIONS (
                $COL_OPERATION_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_OPERATION_NAME TEXT NOT NULL,
                $COL_OPERATION_SOURCE TEXT NOT NULL,
                $COL_OPERATION_DESTINATION TEXT,
                $COL_OPERATION_STATUS TEXT NOT NULL,
                $COL_OPERATION_COUNT INTEGER NOT NULL,
                $COL_OPERATION_TIMESTAMP INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_operation_timestamp ON $TABLE_OPERATIONS ($COL_OPERATION_TIMESTAMP)")
    }

    @Synchronized
    fun recordOperation(item: OperationHistoryItem) {
        val values = ContentValues().apply {
            put(COL_OPERATION_NAME, item.operation)
            put(COL_OPERATION_SOURCE, item.source)
            put(COL_OPERATION_DESTINATION, item.destination)
            put(COL_OPERATION_STATUS, item.status)
            put(COL_OPERATION_COUNT, item.completedCount)
            put(COL_OPERATION_TIMESTAMP, System.currentTimeMillis())
        }
        writableDatabase.insert(TABLE_OPERATIONS, null, values)
        writableDatabase.execSQL("""
            DELETE FROM $TABLE_OPERATIONS WHERE $COL_OPERATION_ID NOT IN (
                SELECT $COL_OPERATION_ID FROM $TABLE_OPERATIONS ORDER BY $COL_OPERATION_TIMESTAMP DESC LIMIT 100
            )
        """.trimIndent())
    }

    @Synchronized
    fun getOperationHistory(limit: Int = 50): List<OperationHistoryItem> {
        val cursor = readableDatabase.query(
            TABLE_OPERATIONS, null, null, null, null, null, "$COL_OPERATION_TIMESTAMP DESC", limit.coerceIn(1, 100).toString()
        )
        return cursor.use {
            buildList {
                while (it.moveToNext()) add(
                    OperationHistoryItem(
                        id = it.getLong(it.getColumnIndexOrThrow(COL_OPERATION_ID)),
                        operation = it.getString(it.getColumnIndexOrThrow(COL_OPERATION_NAME)),
                        source = it.getString(it.getColumnIndexOrThrow(COL_OPERATION_SOURCE)),
                        destination = it.getString(it.getColumnIndexOrThrow(COL_OPERATION_DESTINATION)),
                        status = it.getString(it.getColumnIndexOrThrow(COL_OPERATION_STATUS)),
                        completedCount = it.getInt(it.getColumnIndexOrThrow(COL_OPERATION_COUNT)),
                        timestamp = it.getLong(it.getColumnIndexOrThrow(COL_OPERATION_TIMESTAMP))
                    )
                )
            }
        }
    }

    @Synchronized
    fun insertOrUpdateRecent(item: RecentFileItem) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_RECENT_NAME, item.name)
            put(COL_RECENT_PATH, item.path)
            put(COL_RECENT_SIZE, item.size)
            put(COL_RECENT_IS_DIR, if (item.isDirectory) 1 else 0)
            put(COL_RECENT_ACCESSED, System.currentTimeMillis())
        }
        db.insertWithOnConflict(TABLE_RECENTS, null, values, SQLiteDatabase.CONFLICT_REPLACE)

        // Bounded retention: prune older entries if count exceeds 50
        db.execSQL("""
            DELETE FROM $TABLE_RECENTS WHERE $COL_RECENT_ID NOT IN (
                SELECT $COL_RECENT_ID FROM $TABLE_RECENTS ORDER BY $COL_RECENT_ACCESSED DESC LIMIT 50
            )
        """.trimIndent())
    }

    @Synchronized
    fun getRecentFiles(limit: Int = 20): List<RecentFileItem> {
        val db = readableDatabase
        val list = mutableListOf<RecentFileItem>()
        val cursor = db.query(
            TABLE_RECENTS,
            null,
            null,
            null,
            null,
            null,
            "$COL_RECENT_ACCESSED DESC",
            limit.toString()
        )
        cursor.use {
            while (it.moveToNext()) {
                val item = RecentFileItem(
                    id = it.getLong(it.getColumnIndexOrThrow(COL_RECENT_ID)),
                    name = it.getString(it.getColumnIndexOrThrow(COL_RECENT_NAME)),
                    path = it.getString(it.getColumnIndexOrThrow(COL_RECENT_PATH)),
                    size = it.getLong(it.getColumnIndexOrThrow(COL_RECENT_SIZE)),
                    isDirectory = it.getInt(it.getColumnIndexOrThrow(COL_RECENT_IS_DIR)) == 1,
                    lastAccessed = it.getLong(it.getColumnIndexOrThrow(COL_RECENT_ACCESSED))
                )
                list.add(item)
            }
        }
        return list
    }

    @Synchronized
    fun addFavorite(item: FavoriteItem): Boolean {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_FAV_NAME, item.name)
            put(COL_FAV_PATH, item.path)
            put(COL_FAV_IS_DIR, if (item.isDirectory) 1 else 0)
            put(COL_FAV_ADDED, System.currentTimeMillis())
        }
        val id = db.insertWithOnConflict(TABLE_FAVORITES, null, values, SQLiteDatabase.CONFLICT_IGNORE)
        return id != -1L
    }

    @Synchronized
    fun removeFavorite(path: String): Boolean {
        val db = writableDatabase
        val count = db.delete(TABLE_FAVORITES, "$COL_FAV_PATH = ?", arrayOf(path))
        return count > 0
    }

    @Synchronized
    fun isFavorite(path: String): Boolean {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_FAVORITES,
            arrayOf(COL_FAV_ID),
            "$COL_FAV_PATH = ?",
            arrayOf(path),
            null,
            null,
            null,
            "1"
        )
        return cursor.use { it.count > 0 }
    }

    @Synchronized
    fun getFavorites(): List<FavoriteItem> {
        val db = readableDatabase
        val list = mutableListOf<FavoriteItem>()
        val cursor = db.query(
            TABLE_FAVORITES,
            null,
            null,
            null,
            null,
            null,
            "$COL_FAV_ADDED DESC"
        )
        cursor.use {
            while (it.moveToNext()) {
                val item = FavoriteItem(
                    id = it.getLong(it.getColumnIndexOrThrow(COL_FAV_ID)),
                    name = it.getString(it.getColumnIndexOrThrow(COL_FAV_NAME)),
                    path = it.getString(it.getColumnIndexOrThrow(COL_FAV_PATH)),
                    isDirectory = it.getInt(it.getColumnIndexOrThrow(COL_FAV_IS_DIR)) == 1,
                    addedTimestamp = it.getLong(it.getColumnIndexOrThrow(COL_FAV_ADDED))
                )
                list.add(item)
            }
        }
        return list
    }
}
