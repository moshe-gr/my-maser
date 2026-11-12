package com.example.mymaser.settings.gui

import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.mymaser.R
import com.example.mymaser.gmail.gmailAuthorizationRequest
import com.example.mymaser.settings.GmailSettings
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException

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

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.gmail_title), style = MaterialTheme.typography.bodyMedium)
        Text(
            stringResource(R.string.gmail_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        when {
            !aiEnabled -> Text(
                stringResource(R.string.gmail_needs_ai),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            !settings.connected -> Button(onClick = ::connect) { Text(stringResource(R.string.gmail_connect)) }
            else -> {
                if (settings.needsReconnect) {
                    Text(stringResource(R.string.gmail_reconnect_needed), color = MaterialTheme.colorScheme.error)
                    Button(onClick = ::connect) { Text(stringResource(R.string.gmail_connect)) }
                } else {
                    Text(stringResource(R.string.gmail_connected), color = MaterialTheme.colorScheme.primary)
                }
                OutlinedTextField(
                    value = keywords,
                    onValueChange = { keywords = it },
                    label = { Text(stringResource(R.string.gmail_keywords)) },
                    supportingText = { Text(stringResource(R.string.gmail_keywords_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (keywords != settings.keywords) {
                        Button(onClick = { onKeywordsChange(keywords) }) { Text(stringResource(R.string.save)) }
                    }
                    OutlinedButton(onClick = onCheckNow) { Text(stringResource(R.string.gmail_check_now)) }
                    TextButton(onClick = onDisconnect) { Text(stringResource(R.string.gmail_disconnect)) }
                }
            }
        }
        if (failed) {
            Text(stringResource(R.string.gmail_connect_failed), color = MaterialTheme.colorScheme.error)
        }
    }
}
