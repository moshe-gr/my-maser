package com.example.mymaser.settings.gui

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.mymaser.BuildConfig
import com.example.mymaser.R
import com.example.mymaser.gui.theme.MaserMotion
import com.example.mymaser.reminders.ReminderSettings
import com.example.mymaser.reminders.canPostNotifications
import com.example.mymaser.reminders.needsNotificationPermission
import com.example.mymaser.settings.minuteOfDayToTime
import com.example.mymaser.settings.toMinuteOfDay
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

enum class ReminderToggle { Master, Expected, Balance, Review }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersSetting(
    settings: ReminderSettings,
    minuteOfDay: Int,
    onToggle: (ReminderToggle, Boolean) -> Unit,
    onTimeChange: (Int) -> Unit,
    onRunNow: () -> Unit
) {
    val context = LocalContext.current
    var permissionDenied by rememberSaveable { mutableStateOf(false) }
    var pickingTime by rememberSaveable { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionDenied = !granted
        if (granted) onToggle(ReminderToggle.Master, true)
    }
    val notificationsBlocked = settings.enabled && !canPostNotifications(context)
    val time = minuteOfDayToTime(minuteOfDay)

    fun setMaster(on: Boolean) {
        if (on && needsNotificationPermission(context)) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            onToggle(ReminderToggle.Master, on)
        }
    }

    SwitchRow(
        title = stringResource(R.string.reminders_master),
        subtitle = stringResource(R.string.reminders_master_description),
        checked = settings.enabled,
        onCheckedChange = ::setMaster
    )

    AnimatedVisibility(
        visible = permissionDenied || notificationsBlocked,
        enter = MaserMotion.expand,
        exit = MaserMotion.collapse
    ) {
        SettingRow(title = stringResource(R.string.notifications_blocked)) {
            TextButton(onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                )
            }) { Text(stringResource(R.string.open_settings)) }
        }
    }

    AnimatedVisibility(
        visible = settings.enabled,
        enter = MaserMotion.expand,
        exit = MaserMotion.collapse
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingsDivider()
            listOf(
                Triple(ReminderToggle.Expected, R.string.reminder_expected_title, settings.expectedEntries),
                Triple(ReminderToggle.Balance, R.string.reminder_balance_title, settings.balance),
                Triple(ReminderToggle.Review, R.string.reminder_review_title, settings.yearInReview)
            ).forEach { (toggle, title, checked) ->
                SwitchRow(
                    title = stringResource(title),
                    checked = checked,
                    onCheckedChange = { onToggle(toggle, it) }
                )
            }
            SettingsDivider()
            SettingRow(
                title = stringResource(R.string.reminder_time),
                modifier = Modifier.clickable(
                    onClickLabel = stringResource(R.string.change),
                    role = Role.Button
                ) { pickingTime = true }
            ) {
                Text(
                    time.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (BuildConfig.DEBUG) {
                TextButton(onClick = onRunNow) { Text(stringResource(R.string.run_reminders_now)) }
            }
        }
    }

    if (pickingTime) {
        val state = rememberTimePickerState(initialHour = time.hour, initialMinute = time.minute)
        AlertDialog(
            onDismissRequest = { pickingTime = false },
            title = { Text(stringResource(R.string.reminder_time)) },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    onTimeChange(LocalTime.of(state.hour, state.minute).toMinuteOfDay())
                    pickingTime = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { pickingTime = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}
