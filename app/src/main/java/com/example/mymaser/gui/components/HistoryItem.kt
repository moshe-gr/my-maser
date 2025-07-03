package com.example.mymaser.gui.components

import android.content.Intent
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.example.mymaser.R
import com.example.mymaser.history.History
import com.example.mymaser.history.HistoryRepository.Companion.updateHistory
import java.text.DateFormat
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
            val context = LocalContext.current
            val launcher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument()
            ) {
                it?.let {
                    context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    history.attachmentUri = it.toString()
                    updateHistory(history)
                    attachmentUri = it.toString()
                }
            }
            IconButton(
                onClick = {
                    if (attachmentUri.isNullOrEmpty()) {
                        launcher.launch(arrayOf("image/*", "application/pdf"))
                    } else {
                        attachmentUri?.let { uri ->
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri.toUri(), context.contentResolver.getType(uri.toUri()))
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
                    contentDescription = if (attachmentUri.isNullOrEmpty()) "Add Attachment" else "Show Attachment",
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
                    text = String.format(Locale.getDefault(), "Amount: ₪%,.2f", history.amount),
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