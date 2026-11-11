package com.example.mymaser.settings.gui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.mymaser.R
import com.example.mymaser.gui.components.ScreenColumn
import com.example.mymaser.gui.components.surfaceCardColors
import com.example.mymaser.history.formatAmountInput
import com.example.mymaser.history.sanitizeAmountInput
import com.example.mymaser.gui.LocalDisplayCurrency
import com.example.mymaser.reminders.ReminderSettings
import com.example.mymaser.settings.DEFAULT_REMINDER_MINUTE_OF_DAY
import com.example.mymaser.settings.DEFAULT_MASER_PERCENTAGE
import com.example.mymaser.settings.PRESET_MASER_PERCENTAGES
import com.example.mymaser.settings.SettingsKeys
import com.example.mymaser.settings.SettingsViewModel
import com.example.mymaser.settings.ThemeMode
import com.example.mymaser.settings.formatPercentage
import com.example.mymaser.settings.isValidMaserPercentage

const val LANGUAGE_HEBREW = "he-IL"
private const val LEGACY_LANGUAGE_HEBREW = "iw"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {

    val attachmentDirectoryUri by viewModel.getString(SettingsKeys.ATTACHMENT_DIRECTORY_URI, "")
        .collectAsState(initial = "")
    val language by viewModel.getString(SettingsKeys.LANGUAGE, "")
        .collectAsState(initial = "")
    val themeMode by viewModel.getString(SettingsKeys.THEME_MODE, ThemeMode.System.key)
        .collectAsState(initial = ThemeMode.System.key)
    val rateStatus by viewModel.rateStatus.collectAsState()
    val reminderSettings by viewModel.reminderSettings.collectAsState(initial = ReminderSettings())
    val reminderMinute by viewModel.reminderMinuteOfDay.collectAsState(initial = DEFAULT_REMINDER_MINUTE_OF_DAY)
    val maserPercentage by viewModel.getFloat(SettingsKeys.MASER_PERCENTAGE, DEFAULT_MASER_PERCENTAGE)
        .collectAsState(initial = DEFAULT_MASER_PERCENTAGE)

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
            SettingsSection(stringResource(R.string.settings_maser_percentage)) {
                MaserPercentageSetting(
                    percentage = maserPercentage,
                    onChange = { viewModel.setFloat(SettingsKeys.MASER_PERCENTAGE, it) }
                )
            }

            SettingsSection(stringResource(R.string.settings_currency)) {
                DisplayCurrencySetting(
                    selected = LocalDisplayCurrency.current,
                    status = rateStatus,
                    onConfirm = viewModel::setDisplayCurrency,
                    onRetry = viewModel::retryRates
                )
            }

            SettingsSection(stringResource(R.string.settings_reminders)) {
                RemindersSetting(
                    settings = reminderSettings,
                    minuteOfDay = reminderMinute,
                    onToggle = viewModel::setReminder,
                    onTimeChange = viewModel::setReminderTime,
                    onRunNow = viewModel::runRemindersNow
                )
            }

            SettingsSection(stringResource(R.string.settings_theme)) {
                ChipRow {
                    listOf(
                        ThemeMode.System to R.string.settings_theme_system,
                        ThemeMode.Light to R.string.settings_theme_light,
                        ThemeMode.Dark to R.string.settings_theme_dark
                    ).forEach { (mode, labelRes) ->
                        FilterChip(
                            selected = ThemeMode.fromKey(themeMode) == mode,
                            onClick = { viewModel.setString(SettingsKeys.THEME_MODE, mode.key) },
                            label = { Text(stringResource(labelRes)) }
                        )
                    }
                }
            }

            SettingsSection(stringResource(R.string.settings_language)) {
                val selectedLanguage =
                    if (language == LEGACY_LANGUAGE_HEBREW) LANGUAGE_HEBREW else language
                ChipRow {
                    listOf(
                        "" to R.string.settings_language_system,
                        "en" to R.string.settings_language_en,
                        LANGUAGE_HEBREW to R.string.settings_language_he
                    ).forEach { (code, labelRes) ->
                        FilterChip(
                            selected = selectedLanguage == code,
                            onClick = { viewModel.setString(SettingsKeys.LANGUAGE, code) },
                            label = { Text(stringResource(labelRes)) }
                        )
                    }
                }
            }

            SettingsSection(stringResource(R.string.settings_attachments)) {
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
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() }
        )
        content()
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        content()
    }
}

@Composable
private fun MaserPercentageSetting(percentage: Float, onChange: (Float) -> Unit) {
    val isPreset = percentage in PRESET_MASER_PERCENTAGES
    var customChosen by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val showCustom = customChosen ?: !isPreset
    var customText by rememberSaveable(showCustom) {
        mutableStateOf(if (isPreset) "" else formatAmountInput(percentage))
    }
    val customValue = customText.toFloatOrNull()
    val customError = customText.isNotEmpty() &&
        (customValue == null || !isValidMaserPercentage(customValue))
    val focusRequester = remember { FocusRequester() }

    ChipRow {
        PRESET_MASER_PERCENTAGES.forEach { preset ->
            FilterChip(
                selected = !showCustom && percentage == preset,
                onClick = {
                    customChosen = false
                    onChange(preset)
                },
                label = { Text(formatPercentage(preset)) }
            )
        }
        FilterChip(
            selected = showCustom,
            onClick = { customChosen = true },
            label = { Text(stringResource(R.string.settings_custom)) }
        )
    }

    if (showCustom) {
        LaunchedEffect(Unit) {
            if (customChosen == true) focusRequester.requestFocus()
        }
        OutlinedTextField(
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            value = customText,
            onValueChange = { input ->
                sanitizeAmountInput(input)?.let { text ->
                    customText = text
                    text.toFloatOrNull()?.takeIf(::isValidMaserPercentage)?.let(onChange)
                }
            },
            label = { Text(stringResource(R.string.settings_custom_percentage)) },
            suffix = { Text("%") },
            isError = customError,
            supportingText = if (customError) {
                { Text(stringResource(R.string.settings_percentage_error)) }
            } else {
                null
            },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Done
            )
        )
    }

    Text(
        text = stringResource(R.string.settings_percentage_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
