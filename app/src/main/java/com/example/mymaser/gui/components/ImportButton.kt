package com.example.mymaser.gui.components

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.mymaser.R
import com.example.mymaser.ai.MIME_PDF
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

private val scannerOptions = GmsDocumentScannerOptions.Builder()
    .setGalleryImportAllowed(true)
    .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
    .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
    .build()

/** Lets the user scan a paper document or pick a photo or PDF to read with AI. */
@Composable
fun ImportButton(onDocuments: (List<Uri>) -> Unit, modifier: Modifier = Modifier) {
    val activity = LocalActivity.current
    var menuOpen by remember { mutableStateOf(false) }

    val scanner = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        GmsDocumentScanningResult.fromActivityResultIntent(result.data)?.pdf?.uri
            ?.let { onDocuments(listOf(it)) }
    }
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> onDocuments(uris) }

    Box(modifier = modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { menuOpen = true },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small
        ) {
            Text(stringResource(R.string.import_from_document))
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            if (activity != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.import_scan)) },
                    leadingIcon = { Icon(Icons.Outlined.DocumentScanner, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        GmsDocumentScanning.getClient(scannerOptions)
                            .getStartScanIntent(activity)
                            .addOnSuccessListener { sender ->
                                scanner.launch(IntentSenderRequest.Builder(sender).build())
                            }
                            .addOnFailureListener { picker.launch(arrayOf("image/*", MIME_PDF)) }
                    }
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.import_choose_file)) },
                leadingIcon = { Icon(Icons.Outlined.AttachFile, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    picker.launch(arrayOf("image/*", MIME_PDF))
                }
            )
        }
    }
}
