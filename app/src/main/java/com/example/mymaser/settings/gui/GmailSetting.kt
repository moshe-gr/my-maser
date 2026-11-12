package com.example.mymaser.settings.gui

import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.mymaser.R
import com.example.mymaser.gmail.gmailAuthorizationRequest
import com.example.mymaser.gui.theme.MaserMotion
import com.example.mymaser.settings.GmailSettings
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException

private enum class GmailState { NeedsAi, Disconnected, Reconnect, Connected }

/** Connect Gmail so payslips and receipts are found daily; read-only, always reviewed first. */
@Composable
fun GmailSetting(
    settings: GmailSettings,
    aiEnabled: Boolean,
    onConnected: () -> Unit,
    onDisconnect: () -> Unit,
    onKeywordsChange: (String) -> Unit,
    onCheckNow: () -> Unit
) {
    val activity = LocalActivity.current
    var failed by rememberSaveable { mutableStateOf(false) }
    var keywords by rememberSaveable(settings.keywords) { mutableStateOf(settings.keywords) }

    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val granted = result.resultCode == Activity.RESULT_OK && activity != null && try {
            Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(result.data)
            true
        } catch (_: ApiException) {
            false
        }
        failed = !granted
        if (granted) onConnected()
    }

    fun connect() {
        val host = activity ?: return
        failed = false
        Identity.getAuthorizationClient(host).authorize(gmailAuthorizationRequest())
            .addOnSuccessListener { result ->
                val pending = result.pendingIntent
                if (result.hasResolution() && pending != null) {
                    consent.launch(IntentSenderRequest.Builder(pending.intentSender).build())
                } else {
                    onConnected()
                }
            }
            .addOnFailureListener { failed = true }
    }

    val state = when {
        !aiEnabled -> GmailState.NeedsAi
        !settings.connected -> GmailState.Disconnected
        settings.needsReconnect -> GmailState.Reconnect
        else -> GmailState.Connected
    }

    GmailHeader(state)
    SettingHint(stringResource(R.string.gmail_description))

    AnimatedContent(
        targetState = state,
        transitionSpec = { MaserMotion.swapIn togetherWith MaserMotion.swapOut },
        label = "gmail_state"
    ) { current ->
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (current) {
                GmailState.NeedsAi -> SettingHint(stringResource(R.string.gmail_needs_ai))
                GmailState.Disconnected -> Button(onClick = ::connect) {
                    Text(stringResource(R.string.gmail_connect))
                }
                GmailState.Reconnect, GmailState.Connected -> {
                    if (current == GmailState.Reconnect) {
                        SettingHint(
                            stringResource(R.string.gmail_reconnect_needed),
                            color = MaterialTheme.colorScheme.error
                        )
                        Button(onClick = ::connect) { Text(stringResource(R.string.gmail_connect)) }
                    }
                    OutlinedTextField(
                        value = keywords,
                        onValueChange = { keywords = it },
                        label = { Text(stringResource(R.string.gmail_keywords)) },
                        supportingText = { Text(stringResource(R.string.gmail_keywords_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        AnimatedVisibility(
                            visible = keywords != settings.keywords,
                            enter = MaserMotion.swapIn,
                            exit = MaserMotion.swapOut
                        ) {
                            Button(onClick = { onKeywordsChange(keywords) }) {
                                Text(stringResource(R.string.save))
                            }
                        }
                        OutlinedButton(onClick = onCheckNow) { Text(stringResource(R.string.gmail_check_now)) }
                        TextButton(onClick = onDisconnect) { Text(stringResource(R.string.gmail_disconnect)) }
                    }
                }
            }
        }
    }

    AnimatedVisibility(visible = failed, enter = MaserMotion.expand, exit = MaserMotion.collapse) {
        SettingHint(
            stringResource(R.string.gmail_connect_failed),
            color = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun GmailHeader(state: GmailState) {
    val colors = MaterialTheme.colorScheme
    val (label, container, content) = when (state) {
        GmailState.Connected -> Triple(R.string.gmail_connected, colors.primaryContainer, colors.onPrimaryContainer)
        GmailState.Reconnect -> Triple(R.string.gmail_status_reconnect, colors.errorContainer, colors.onErrorContainer)
        GmailState.Disconnected ->
            Triple(R.string.gmail_status_not_connected, colors.surfaceContainerHigh, colors.onSurfaceVariant)
        GmailState.NeedsAi -> Triple(R.string.gmail_status_off, colors.surfaceContainerHigh, colors.onSurfaceVariant)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Outlined.Mail,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            stringResource(R.string.gmail_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f)
        )
        StatusPill(text = stringResource(label), container = container, content = content)
    }
}
