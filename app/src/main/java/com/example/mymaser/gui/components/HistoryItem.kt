package com.example.mymaser.gui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.datastore.preferences.core.Preferences
import androidx.documentfile.provider.DocumentFile
import com.example.mymaser.R
import com.example.mymaser.dataStore
import com.example.mymaser.history.History
import com.example.mymaser.history.HistoryRepository.Companion.updateHistory
import com.example.mymaser.settings.SettingsKeys
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Preview
@Composable
fun HistoryItem(history: History = History()) {
    val dateFormatter = remember {
        DateFormat.getDateTimeInstance(
            DateFormat.MEDIUM,
            DateFormat.SHORT,
            Locale.getDefault()
        )
    }
    var attachmentUri by remember { mutableStateOf(history.attachmentUri) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            scope.launch {
                val newUri = saveAttachment(context, history, it)
                if (newUri != null) {
                    history.attachmentUri = newUri.toString()
                    updateHistory(history)
                    attachmentUri = newUri.toString()
                }
            }
        }
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 16.dp),
        elevation = 2.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(color = colorResource(id = if (history.isDonation) R.color.donation_red else R.color.income_green))
                .padding(12.dp)
        ) {
            IconButton(
                onClick = {
                    if (attachmentUri.isNullOrEmpty()) {
                        launcher.launch(arrayOf("image/*", "application/pdf"))
                    } else {
                        attachmentUri?.let { uriString ->
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(
                                    uriString.toUri(),
                                    context.contentResolver.getType(uriString.toUri())
                                )
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(intent)
                        }
                    }
                },
                modifier = Modifier.size(16.dp)
            ) {
                Icon(
                    imageVector = if (attachmentUri.isNullOrEmpty()) Icons.Filled.Add else Icons.Filled.AttachFile,
                    contentDescription = if (attachmentUri.isNullOrEmpty()) stringResource(id = R.string.add_attachment) else stringResource(id = R.string.show_attachment),
                    tint = colorResource(id = R.color.colorPrimary)
                )
            }
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = history.name,
                    color = colorResource(id = R.color.colorPrimary),
                    style = MaterialTheme.typography.h6
                )
                Spacer(modifier = Modifier.padding(vertical = 2.dp))
                Text(
                    text = stringResource(id = R.string.amount_format, history.amount),
                    color = colorResource(id = R.color.colorPrimary),
                    style = MaterialTheme.typography.body1
                )
                Spacer(modifier = Modifier.padding(vertical = 2.dp))
                Text(
                    text = dateFormatter.format(Date(history.timeStamp)),
                    color = colorResource(id = R.color.colorPrimary).copy(alpha = 0.8f),
                    style = MaterialTheme.typography.caption
                )
            }
        }
    }
}

private fun getFileName(context: Context, uri: Uri): String {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        try {
            if (cursor != null && cursor.moveToFirst()) {
                val columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (columnIndex != -1) {
                    result = cursor.getString(columnIndex)
                }
            }
        } finally {
            cursor?.close()
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/')
        if (cut != -1) {
            if (cut != null) {
                result = result.substring(cut + 1)
            }
        }
    }
    return result ?: "${context.getString(R.string.attachment_prefix)}${System.currentTimeMillis()}"
}


private suspend fun saveAttachment(context: Context, history: History, uri: Uri): Uri? {
    val attachmentDirectoryUriString = context.dataStore.data.map { preferences ->
        preferences[SettingsKeys.ATTACHMENT_DIRECTORY_URI] ?: ""
    }.first()

    if (attachmentDirectoryUriString.isBlank()) {
        Toast.makeText(
            context,
            context.getString(R.string.please_select_a_directory_in_settings),
            Toast.LENGTH_SHORT
        ).show()
        return null
    }

    val treeUri = attachmentDirectoryUriString.toUri()
    val rootDir = DocumentFile.fromTreeUri(context, treeUri) ?: return null

    val folderNameKey: Preferences.Key<String>
    val defaultFolderName: String
    if (history.isDonation) {
        folderNameKey = SettingsKeys.PUBLIC_DONATION_NAME
        defaultFolderName = context.getString(R.string.donations)
    } else {
        folderNameKey = SettingsKeys.PUBLIC_INCOME_NAME
        defaultFolderName = context.getString(R.string.incomes)
    }
    val publicFolderName = context.dataStore.data.map { preferences ->
        preferences[folderNameKey] ?: defaultFolderName
    }.first()

    val year = Calendar.getInstance().get(Calendar.YEAR).toString()
    val historyName = history.name
    val fileName = getFileName(context, uri)

    var currentDir: DocumentFile? = rootDir
    listOf(publicFolderName, year, historyName).forEach { segment ->
        if (currentDir == null) return@forEach
        val nextDir = currentDir.findFile(segment)
        currentDir = if (nextDir != null && nextDir.isDirectory) {
            nextDir
        } else {
            currentDir.createDirectory(segment)
        }
    }

    currentDir?.let { finalDir ->
        finalDir.findFile(fileName)?.delete()
        val newFile = finalDir.createFile(context.contentResolver.getType(uri) ?: "application/octet-stream", fileName)

        newFile?.uri?.let { destUri ->
            context.contentResolver.openOutputStream(destUri)?.use { outputStream ->
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            return destUri
        }
    }
    return null
}
