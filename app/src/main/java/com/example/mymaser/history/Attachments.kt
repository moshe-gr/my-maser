package com.example.mymaser.history

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.example.mymaser.R
import com.example.mymaser.dataStore
import com.example.mymaser.settings.SettingsKeys
import java.io.IOException
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

sealed interface AttachmentResult {
    data class Saved(val uri: Uri) : AttachmentResult
    data object NoDirectory : AttachmentResult
    data object Failed : AttachmentResult
}

/** The folder an entry type's attachments go in; a blank or unset name means the default one. */
fun Context.attachmentFolderName(isDonation: Boolean, stored: String?): String =
    stored?.takeIf { it.isNotBlank() }
        ?: getString(if (isDonation) R.string.donations else R.string.incomes)

/** Copies [source] into <folder>/<entry year>/<entry name>/ under the chosen attachments directory. */
suspend fun saveAttachment(context: Context, history: History, source: Uri): AttachmentResult {
    val prefs = context.dataStore.data.first()
    val directory = prefs[SettingsKeys.ATTACHMENT_DIRECTORY_URI]
    if (directory.isNullOrBlank()) return AttachmentResult.NoDirectory
    val folderKey = if (history.isDonation) SettingsKeys.PUBLIC_DONATION_NAME else SettingsKeys.PUBLIC_INCOME_NAME
    val folderName = context.attachmentFolderName(history.isDonation, prefs[folderKey])

    val saved = withContext(Dispatchers.IO) {
        try {
            copyToAttachmentFolder(context, directory, folderName, history, source)
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        }
    }
    return saved?.let(AttachmentResult::Saved) ?: AttachmentResult.Failed
}

private fun displayName(context: Context, uri: Uri): String {
    val queried = if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (column != -1 && cursor.moveToFirst()) cursor.getString(column) else null
        }
    } else {
        null
    }
    return queried?.takeIf { it.isNotBlank() }
        ?: uri.path?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
        ?: "${context.getString(R.string.attachment_prefix)}${System.currentTimeMillis()}"
}

private fun copyToAttachmentFolder(
    context: Context,
    attachmentDirectoryUri: String,
    folderName: String,
    history: History,
    source: Uri
): Uri? {
    val rootDir = DocumentFile.fromTreeUri(context, attachmentDirectoryUri.toUri()) ?: return null
    val year = history.localDate(ZoneId.systemDefault()).year.toString()

    var dir: DocumentFile = rootDir
    for (segment in listOf(folderName, year, history.name)) {
        dir = dir.findFile(segment)?.takeIf { it.isDirectory }
            ?: dir.createDirectory(segment)
            ?: return null
    }

    val resolver = context.contentResolver
    val newFile = dir.createFile(
        resolver.getType(source) ?: "application/octet-stream",
        displayName(context, source)
    ) ?: return null
    try {
        val copied = resolver.openOutputStream(newFile.uri)?.use { output ->
            resolver.openInputStream(source)?.use { input -> input.copyTo(output) }
        }
        if (copied == null) throw IOException("Couldn't open $source or ${newFile.uri}")
    } catch (e: Exception) {
        newFile.delete()
        throw e
    }
    return newFile.uri
}
