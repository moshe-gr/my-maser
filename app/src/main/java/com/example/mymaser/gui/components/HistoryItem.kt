package com.example.mymaser.gui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.example.mymaser.R
import com.example.mymaser.gui.formatCurrency
import com.example.mymaser.gui.formatSignedCurrency
import com.example.mymaser.gui.theme.maserColors
import com.example.mymaser.gui.theme.tabular
import com.example.mymaser.history.AttachmentResult
import com.example.mymaser.history.History
import com.example.mymaser.history.hasAttachment
import com.example.mymaser.history.saveAttachment
import java.util.Date
import kotlinx.coroutines.launch

@Composable
fun HistoryItem(
    history: History,
    onClick: () -> Unit,
    onAttachmentSaved: (String) -> Unit,
    modifier: Modifier = Modifier,
    original: History? = null
) {
    val dateFormatter = rememberDateTimeFormat()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            scope.launch {
                when (val result = saveAttachment(context, history, it)) {
                    is AttachmentResult.Saved -> onAttachmentSaved(result.uri.toString())
                    AttachmentResult.NoDirectory ->
                        context.toast(R.string.please_select_a_directory_in_settings)
                    AttachmentResult.Failed -> context.toast(R.string.attachment_save_failed)
                }
            }
        }
    }

    val accent = if (history.isDonation) MaterialTheme.maserColors.donation else MaterialTheme.maserColors.income
    val hasAttachment = history.hasAttachment

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
            EntryTypeBadge(history.isDonation)
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
                        openAttachment(context, history.attachmentUri.orEmpty())
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

private fun openAttachment(context: Context, attachmentUri: String) {
    try {
        val uri = attachmentUri.toUri()
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, context.contentResolver.getType(uri))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        context.toast(R.string.cannot_open_attachment)
    } catch (_: SecurityException) {
        context.toast(R.string.cannot_open_attachment)
    }
}

private fun Context.toast(@StringRes message: Int) {
    Toast.makeText(this, getString(message), Toast.LENGTH_SHORT).show()
}
