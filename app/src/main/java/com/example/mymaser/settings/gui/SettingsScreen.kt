package com.example.mymaser.settings.gui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Percent
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.example.mymaser.R
import com.example.mymaser.gui.LocalDisplayCurrency
import com.example.mymaser.gui.components.ScreenColumn
import com.example.mymaser.gui.theme.MaserMotion
import com.example.mymaser.history.formatAmountInput
import com.example.mymaser.history.sanitizeAmountInput
import com.example.mymaser.settings.PRESET_MASER_PERCENTAGES
import com.example.mymaser.settings.SettingsKeys
import com.example.mymaser.settings.SettingsViewModel
import com.example.mymaser.settings.ThemeMode
import com.example.mymaser.settings.formatPercentage
import com.example.mymaser.settings.isValidMaserPercentage

private const val LANGUAGE_HEBREW = "he-IL"
private const val LEGACY_LANGUAGE_HEBREW = "iw"
private const val CUSTOM_PERCENTAGE = -1f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {

    val loaded by viewModel.state.collectAsState()
    val state = loaded ?: return
    val rateStatus by viewModel.rateStatus.collectAsState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
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
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { paddingValues ->
        ScreenColumn(
            modifier = Modifier
                .padding(paddingValues)
                .consumeWindowInsets(paddingValues)
                .imePadding()
        ) {
            SettingsSection(stringResource(R.string.settings_maser_percentage), Icons.Outlined.Percent) {
                MaserPercentageSetting(
                    percentage = state.maserPercentage,
                    onChange = { viewModel.set(SettingsKeys.MASER_PERCENTAGE, it) }
                )
            }

            SettingsSection(stringResource(R.string.settings_currency), Icons.Outlined.Payments) {
                DisplayCurrencySetting(
                    selected = LocalDisplayCurrency.current,
                    status = rateStatus,
                    onConfirm = viewModel::setDisplayCurrency,
                    onRetry = viewModel::retryRates
                )
            }

            SettingsSection(stringResource(R.string.settings_ai), Icons.Outlined.AutoAwesome) {
                AiSetting(
                    enabled = state.aiEnabled,
                    basis = state.maserBasis,
                    onEnabledChange = viewModel::setAiEnabled,
                    onBasisChange = { viewModel.set(SettingsKeys.MASER_BASIS, it.key) }
                )
                SettingsDivider()
                PdfPasswordSetting(
                    saved = state.hasPdfPassword,
                    onSave = viewModel::savePdfPassword,
                    onRemove = viewModel::removePdfPassword
                )
                SettingsDivider()
                GmailSetting(
                    settings = state.gmail,
                    aiEnabled = state.aiEnabled,
                    onConnected = viewModel::gmailConnected,
                    onDisconnect = viewModel::disconnectGmail,
                    onKeywordsChange = viewModel::setGmailKeywords,
                    onCheckNow = viewModel::checkGmailNow
                )
            }

            SettingsSection(stringResource(R.string.settings_reminders), Icons.Outlined.Notifications) {
                RemindersSetting(
                    settings = state.reminders,
                    minuteOfDay = state.reminderMinuteOfDay,
                    onToggle = viewModel::setReminder,
                    onTimeChange = viewModel::setReminderTime,
                    onRunNow = viewModel::runRemindersNow
                )
            }

            SettingsSection(stringResource(R.string.settings_theme), Icons.Outlined.Palette) {
                SegmentedChoice(
                    options = listOf(
                        ThemeMode.System to stringResource(R.string.settings_theme_system),
                        ThemeMode.Light to stringResource(R.string.settings_theme_light),
                        ThemeMode.Dark to stringResource(R.string.settings_theme_dark)
                    ),
                    selected = state.themeMode,
                    onSelect = { viewModel.setString(SettingsKeys.THEME_MODE, it.key) }
                )
            }

            SettingsSection(stringResource(R.string.settings_language), Icons.Outlined.Language) {
                val selectedLanguage =
                    if (state.language == LEGACY_LANGUAGE_HEBREW) LANGUAGE_HEBREW else state.language
                SegmentedChoice(
                    options = listOf(
                        "" to stringResource(R.string.settings_language_system),
                        "en" to stringResource(R.string.settings_language_en),
                        LANGUAGE_HEBREW to stringResource(R.string.settings_language_he)
                    ),
                    selected = selectedLanguage,
                    onSelect = { viewModel.setString(SettingsKeys.LANGUAGE, it) }
                )
            }
        }
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

    SegmentedChoice(
        options = PRESET_MASER_PERCENTAGES.map { it to formatPercentage(it) } +
            (CUSTOM_PERCENTAGE to stringResource(R.string.settings_custom)),
        selected = if (showCustom) CUSTOM_PERCENTAGE else percentage,
        onSelect = { choice ->
            if (choice == CUSTOM_PERCENTAGE) {
                customChosen = true
            } else {
                customChosen = false
                onChange(choice)
            }
        }
    )

    AnimatedVisibility(visible = showCustom, enter = MaserMotion.expand, exit = MaserMotion.collapse) {
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

    SettingHint(stringResource(R.string.settings_percentage_hint))
}
