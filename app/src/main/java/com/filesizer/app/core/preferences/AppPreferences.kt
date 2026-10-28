package com.filesizer.app.core.preferences

import android.content.Context

enum class ThemePreference {
    SYSTEM,
    LIGHT,
    DARK
}

class AppPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    var themePreference: ThemePreference
        get() = runCatching {
            ThemePreference.valueOf(preferences.getString(KEY_THEME, null) ?: ThemePreference.SYSTEM.name)
        }.getOrDefault(ThemePreference.SYSTEM)
        set(value) {
            preferences.edit().putString(KEY_THEME, value.name).apply()
        }

    var showHiddenFiles: Boolean
        get() = preferences.getBoolean(KEY_SHOW_HIDDEN_FILES, false)
        set(value) {
            preferences.edit().putBoolean(KEY_SHOW_HIDDEN_FILES, value).apply()
        }

    var documentTreeUri: String?
        get() = preferences.getString(KEY_DOCUMENT_TREE_URI, null)
        set(value) {
            preferences.edit().putString(KEY_DOCUMENT_TREE_URI, value).apply()
        }

    private companion object {
        const val PREFERENCES_NAME = "file_sizer_preferences"
        const val KEY_THEME = "theme_preference"
        const val KEY_SHOW_HIDDEN_FILES = "show_hidden_files"
        const val KEY_DOCUMENT_TREE_URI = "document_tree_uri"
    }
}
