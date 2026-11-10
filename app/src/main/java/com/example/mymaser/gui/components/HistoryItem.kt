package com.example.mymaser.gui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.datastore.preferences.core.Preferences
import androidx.documentfile.provider.DocumentFile
import com.example.mymaser.R
import com.example.mymaser.dataStore
import com.example.mymaser.gui.formatCurrency
import com.example.mymaser.gui.formatSignedCurrency
import com.example.mymaser.gui.theme.maserColors
import com.example.mymaser.gui.theme.tabular
import com.example.mymaser.history.History
import com.example.mymaser.settings.SettingsKeys
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun HistoryItem(
    history: History,
    onClick: () -> Unit,
    onAttachmentSaved: (String) -> Unit,
    modifier: Modifier = Modifier,
    original: History? = null
) {
    val dateFormatter = remember {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            scope.launch {
                saveAttachment(context, history, it)?.let { saved ->
                    onAttachmentSaved(saved.toString())
                }
            }
        }
    }

    val colors = MaterialTheme.maserColors
    val accent = if (history.isDonation) colors.donation else colors.income
    val hasAttachment = !history.attachmentUri.isNullOrEmpty()

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = surfaceCardColors()
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = if (history.isDonation) colors.donationContainer else colors.incomeContainer,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (history.isDonation) {
                        Icons.Filled.VolunteerActivism
                    } else {
                        Icons.Filled.AccountBalanceWallet
                    },
                    contentDescription = stringResource(
                        if (history.isDonation) R.string.donation else R.string.income
                    ),
                    tint = accent,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = history.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = dateFormatter.format(Date(history.timeStamp)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatSignedCurrency(
                        history.amount.toDouble(),
                        history.isDonation,
                        history.currency
                    ),
                    style = MaterialTheme.typography.titleMedium.tabular,
                    color = accent
                )
                if (original != null && original.currency != history.currency) {
                    Text(
                        text = stringResource(
                            R.string.originally_amount,
                            formatCurrency(original.amount.toDouble(), currencyCode = original.currency)
                        ),
                        style = MaterialTheme.typography.bodySmall.tabular,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(
                onClick = {
                    if (hasAttachment) {
                        val uri = history.attachmentUri.toUri()
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, context.contentResolver.getType(uri))
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(intent)
                    } else {
                        launcher.launch(arrayOf("image/*", "application/pdf"))
                    }
                }
            ) {
                Icon(
                    imageVector = if (hasAttachment) Icons.Filled.Description else Icons.Outlined.UploadFile,
                    contentDescription = stringResource(
                        if (hasAttachment) R.string.show_attachment else R.string.add_attachment
                    ),
                    tint = if (hasAttachment) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

private fun getFileName(context: Context, uri: Uri): String {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor.use { cursor ->
            if (cursor != null && cursor.moveToFirst()) {
                val columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (columnIndex != -1) {
                    result = cursor.getString(columnIndex)
                }
            }
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

    return withContext(Dispatchers.IO) {
        copyToAttachmentFolder(context, attachmentDirectoryUriString, publicFolderName, history, uri)
    }
}

private fun copyToAttachmentFolder(
    context: Context,
    attachmentDirectoryUri: String,
    publicFolderName: String,
    history: History,
    uri: Uri
): Uri? {
    val rootDir = DocumentFile.fromTreeUri(context, attachmentDirectoryUri.toUri()) ?: return null
    val year = Calendar.getInstance().get(Calendar.YEAR).toString()
    val fileName = getFileName(context, uri)

    var currentDir: DocumentFile? = rootDir
    listOf(publicFolderName, year, history.name).forEach { segment ->
        val parent = currentDir ?: return@forEach
        val nextDir = parent.findFile(segment)
        currentDir = if (nextDir != null && nextDir.isDirectory) {
            nextDir
        } else {
            parent.createDirectory(segment)
        }
    }

    currentDir?.let { finalDir ->
        finalDir.findFile(fileName)?.delete()
        val newFile = finalDir.createFile(
            context.contentResolver.getType(uri) ?: "application/octet-stream",
            fileName
        )

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
