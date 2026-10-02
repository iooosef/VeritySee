package com.example.annotator.storage

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.recentFoldersDataStore by preferencesDataStore(name = "recent_folders")

/**
 * Recently opened dataset folders (tree URI strings), most recent first. Stored as a single
 * newline-joined string rather than a `stringSetPreferencesKey`, since Preferences sets do not
 * preserve insertion order.
 */
class RecentFolders(private val context: Context) {

    private val key = stringPreferencesKey("recent_folder_uris")

    val recentUris: Flow<List<String>> = context.recentFoldersDataStore.data.map { prefs ->
        prefs[key]?.split("\n")?.filter { it.isNotEmpty() }.orEmpty()
    }

    suspend fun addRecent(uri: String, maxEntries: Int = 10) {
        context.recentFoldersDataStore.edit { prefs ->
            val current = prefs[key]?.split("\n")?.filter { it.isNotEmpty() }?.toMutableList() ?: mutableListOf()
            current.remove(uri)
            current.add(0, uri)
            prefs[key] = current.take(maxEntries).joinToString("\n")
        }
    }

    suspend fun removeRecent(uri: String) {
        context.recentFoldersDataStore.edit { prefs ->
            val current = prefs[key]?.split("\n")?.filter { it.isNotEmpty() }.orEmpty()
            prefs[key] = (current - uri).joinToString("\n")
        }
    }
}
