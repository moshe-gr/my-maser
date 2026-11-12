package com.example.mymaser.settings.data

import android.content.Context
import androidx.core.net.toUri
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.documentfile.provider.DocumentFile
import com.example.mymaser.dataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class SettingsRepository(context: Context) {

    private val dataStore = context.dataStore
    private val appContext: Context = context.applicationContext

    fun <T> get(key: Preferences.Key<T>, defaultValue: T): Flow<T> =
        dataStore.data.map { it[key] ?: defaultValue }

    suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        dataStore.edit { it[key] = value }
    }

    suspend fun renameDirectory(
        oldName: String,
        newName: String,
        attachmentDirectoryUri: String
    ) {
        withContext(Dispatchers.IO) {
            if (attachmentDirectoryUri.isNotBlank() && newName.isNotBlank() && oldName != newName) {
                val treeUri = attachmentDirectoryUri.toUri()
                val rootDir = DocumentFile.fromTreeUri(appContext, treeUri)

                rootDir?.findFile(oldName)
                    ?.takeIf { it.isDirectory }
                    ?.renameTo(newName)
                    ?: rootDir?.createDirectory(newName)
            }
        }
    }
}
