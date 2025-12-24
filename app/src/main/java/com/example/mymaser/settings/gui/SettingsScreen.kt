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
import androidx.compose.material.MaterialTheme.typography
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.mymaser.R
import com.example.mymaser.settings.SettingsKeys
import com.example.mymaser.settings.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val attachmentDirectoryUri by viewModel.getString(
        SettingsKeys.ATTACHMENT_DIRECTORY_URI,
        ""
    ).collectAsState(initial = "")
    val initialDonationDirectoryName by viewModel.getString(
        SettingsKeys.PUBLIC_DONATION_NAME,
        stringResource(R.string.donations)
    ).collectAsState(initial = stringResource(R.string.donations))
    val initialIncomeDirectoryName by viewModel.getString(
        SettingsKeys.PUBLIC_INCOME_NAME,
        stringResource(R.string.incomes)
    ).collectAsState(initial = stringResource(R.string.incomes))

    var donationDirectoryName by remember { mutableStateOf(initialDonationDirectoryName) }
    var incomeDirectoryName by remember { mutableStateOf(initialIncomeDirectoryName) }
    var lastSavedDonationName by remember { mutableStateOf(initialDonationDirectoryName) }
    var lastSavedIncomeName by remember { mutableStateOf(initialIncomeDirectoryName) }

    LaunchedEffect(initialDonationDirectoryName) {
        donationDirectoryName = initialDonationDirectoryName
        lastSavedDonationName = initialDonationDirectoryName
    }

    LaunchedEffect(initialIncomeDirectoryName) {
        incomeDirectoryName = initialIncomeDirectoryName
        lastSavedIncomeName = initialIncomeDirectoryName
    }

    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val renameDirectory = { oldName: String, newName: String ->
        if (attachmentDirectoryUri.isNotBlank() && newName.isNotBlank() && oldName != newName) {
            val treeUri = attachmentDirectoryUri.toUri()
            val rootDir = DocumentFile.fromTreeUri(context, treeUri)
            val oldDir = rootDir?.findFile(oldName)
            if (oldDir != null && oldDir.isDirectory) {
                oldDir.renameTo(newName)
            } else {
                // If the directory doesn't exist, create it.
                rootDir?.createDirectory(newName)
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                if (donationDirectoryName != lastSavedDonationName) {
                    coroutineScope.launch {
                        withContext(Dispatchers.IO) {
                            renameDirectory(lastSavedDonationName, donationDirectoryName)
                        }
                        viewModel.setString(SettingsKeys.PUBLIC_DONATION_NAME, donationDirectoryName)
                    }
                }
                if (incomeDirectoryName != lastSavedIncomeName) {
                    coroutineScope.launch {
                        withContext(Dispatchers.IO) {
                            renameDirectory(lastSavedIncomeName, incomeDirectoryName)
                        }
                        viewModel.setString(SettingsKeys.PUBLIC_INCOME_NAME, incomeDirectoryName)
                    }
                }
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (donationDirectoryName != lastSavedDonationName) {
                coroutineScope.launch {
                    withContext(Dispatchers.IO) {
                        renameDirectory(lastSavedDonationName, donationDirectoryName)
                    }
                    viewModel.setString(SettingsKeys.PUBLIC_DONATION_NAME, donationDirectoryName)
                }
            }
            if (incomeDirectoryName != lastSavedIncomeName) {
                coroutineScope.launch {
                    withContext(Dispatchers.IO) {
                        renameDirectory(lastSavedIncomeName, incomeDirectoryName)
                    }
                    viewModel.setString(SettingsKeys.PUBLIC_INCOME_NAME, incomeDirectoryName)
                }
            }
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val contentResolver = context.contentResolver
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            coroutineScope.launch {
                viewModel.setString(SettingsKeys.ATTACHMENT_DIRECTORY_URI, uri.toString())
            }
        }
    }

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
                style = typography.h5
            )
            Spacer(modifier = Modifier.size(16.dp))

            Button(onClick = { launcher.launch(null) }) {
                Text(text = stringResource(R.string.select_attachments_directory))
            }
            Spacer(modifier = Modifier.size(8.dp))
            if (attachmentDirectoryUri.isNotBlank()) {
                val treeUri = attachmentDirectoryUri.toUri()
                val rootDir = DocumentFile.fromTreeUri(context, treeUri)
                Text(
                    text = stringResource(id = R.string.selected_directory, rootDir?.name ?: ""),
                    style = typography.body2
                )
            } else {
                Text(
                    text = stringResource(R.string.select_directory_to_enable),
                    style = typography.caption
                )
            }

            Spacer(modifier = Modifier.size(24.dp))
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = donationDirectoryName,
                onValueChange = { newValue ->
                    donationDirectoryName = newValue
                },
                label = { Text(stringResource(R.string.donation_attachments_directory)) },
                enabled = attachmentDirectoryUri.isNotBlank()
            )
            Spacer(modifier = Modifier.size(16.dp))
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = incomeDirectoryName,
                onValueChange = { newValue ->
                    incomeDirectoryName = newValue
                },
                label = { Text(stringResource(R.string.income_attachments_directory)) },
                enabled = attachmentDirectoryUri.isNotBlank()
            )
        }
    }
}
