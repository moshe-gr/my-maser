package com.example.mymaser.settings.gui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.mymaser.R
import com.example.mymaser.settings.SettingsKeys
import com.example.mymaser.settings.SettingsViewModel

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {

    /* ---------- Persisted state (DataStore) ---------- */

    val attachmentDirectoryUri by viewModel.getString(
        SettingsKeys.ATTACHMENT_DIRECTORY_URI,
        ""
    ).collectAsState(initial = "")

    /* ---------- Helpers ---------- */

    val context = LocalContext.current
    rememberCoroutineScope()

    /* ---------- SAVE WHEN SCREEN IS LEFT (ALL CASES) ---------- */

    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                viewModel.onPause(
                    currentDonationName = viewModel.donationDirectoryName,
                    currentIncomeName = viewModel.incomeDirectoryName,
                    attachmentDirectoryUri = attachmentDirectoryUri
                )
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    /* ---------- Directory picker ---------- */

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            viewModel.setString(
                SettingsKeys.ATTACHMENT_DIRECTORY_URI,
                uri.toString()
            )
        }
    }

    /* ---------- UI ---------- */

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(colorResource(id = R.color.colorPrimary))
            .systemBarsPadding(),
        backgroundColor = colorResource(id = R.color.bg_color)
    ) { paddingValues ->

        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text(
                text = stringResource(R.string.settings),
                style = MaterialTheme.typography.h5
            )

            Spacer(Modifier.size(16.dp))

            Button(onClick = { launcher.launch(null) }) {
                Text(stringResource(R.string.select_attachments_directory))
            }

            Spacer(Modifier.size(8.dp))

            if (attachmentDirectoryUri.isNotBlank()) {
                val rootDir = DocumentFile.fromTreeUri(
                    context,
                    attachmentDirectoryUri.toUri()
                )
                Text(
                    text = stringResource(
                        R.string.selected_directory,
                        rootDir?.name ?: ""
                    ),
                    style = MaterialTheme.typography.body2
                )
            } else {
                Text(
                    text = stringResource(R.string.select_directory_to_enable),
                    style = MaterialTheme.typography.caption
                )
            }

            Spacer(Modifier.size(24.dp))

            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = viewModel.donationDirectoryName,
                onValueChange = {
                    viewModel.updateDonationName(it)
                },
                label = {
                    Text(stringResource(R.string.donation_attachments_directory))
                },
                enabled = attachmentDirectoryUri.isNotBlank()
            )

            Spacer(Modifier.size(16.dp))

            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = viewModel.incomeDirectoryName,
                onValueChange = {
                    viewModel.updateIncomeName(it)
                },
                label = {
                    Text(stringResource(R.string.income_attachments_directory))
                },
                enabled = attachmentDirectoryUri.isNotBlank()
            )
        }
    }
}
