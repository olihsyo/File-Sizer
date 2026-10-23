package com.filesizer.app.core.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.filesizer.app.core.database.model.FavoriteItem
import com.filesizer.app.core.database.model.RecentFileItem

class FileSizerDbHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "filesizer.db"
        const val DATABASE_VERSION = 1

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
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_RECENTS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_FAVORITES")
        onCreate(db)
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
