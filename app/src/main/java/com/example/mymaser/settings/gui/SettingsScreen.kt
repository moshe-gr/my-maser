package com.example.mymaser.settings.gui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.mymaser.R
import com.example.mymaser.gui.components.ScreenColumn
import com.example.mymaser.gui.components.surfaceCardColors
import com.example.mymaser.settings.SettingsKeys
import com.example.mymaser.settings.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {

    val attachmentDirectoryUri by viewModel.getString(
        SettingsKeys.ATTACHMENT_DIRECTORY_URI,
        ""
    ).collectAsState(initial = "")

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, attachmentDirectoryUri) {
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

    val hasDirectory = attachmentDirectoryUri.isNotBlank()
    val directoryStatus = if (hasDirectory) {
        stringResource(
            R.string.selected_directory,
            DocumentFile.fromTreeUri(context, attachmentDirectoryUri.toUri())?.name.orEmpty()
        )
    } else {
        stringResource(R.string.no_directory_selected)
    }
    val disabledHelper: @Composable (() -> Unit)? = if (hasDirectory) {
        null
    } else {
        { Text(stringResource(R.string.select_directory_to_enable)) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        ScreenColumn(
            modifier = Modifier
                .padding(paddingValues)
                .consumeWindowInsets(paddingValues)
                .imePadding()
        ) {
            Card(
                onClick = { launcher.launch(null) },
                modifier = Modifier.fillMaxWidth(),
                colors = surfaceCardColors()
            ) {
                ListItem(
                    headlineContent = {
                        Text(stringResource(R.string.select_attachments_directory))
                    },
                    supportingContent = { Text(directoryStatus) },
                    leadingContent = { Icon(Icons.Outlined.Folder, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }

            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = viewModel.donationDirectoryName,
                onValueChange = viewModel::updateDonationName,
                label = { Text(stringResource(R.string.donation_attachments_directory)) },
                supportingText = disabledHelper,
                enabled = hasDirectory,
                singleLine = true,
                shape = MaterialTheme.shapes.small
            )

            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = viewModel.incomeDirectoryName,
                onValueChange = viewModel::updateIncomeName,
                label = { Text(stringResource(R.string.income_attachments_directory)) },
                supportingText = disabledHelper,
                enabled = hasDirectory,
                singleLine = true,
                shape = MaterialTheme.shapes.small
            )
        }
    }
}
