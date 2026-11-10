package com.example.mymaser.settings.gui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import com.example.mymaser.currency.RateStatus
import com.example.mymaser.currency.SupportedCurrency

/**
 * Display-currency chips. Switching asks for confirmation first, since it changes every amount
 * shown, then reports the exchange-rate download.
 */
@Composable
fun DisplayCurrencySetting(
    selected: String,
    status: RateStatus,
    onConfirm: (String) -> Unit,
    onRetry: () -> Unit
) {
    var pending by rememberSaveable { mutableStateOf<String?>(null) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SupportedCurrency.entries.forEach { currency ->
            FilterChip(
                selected = selected == currency.code,
                onClick = { if (currency.code != selected) pending = currency.code },
                label = { Text("${currency.symbol}  ${stringResource(currency.nameRes)}") }
            )
        }
    }

    when (status) {
        RateStatus.Loading -> Row(
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Text(
                text = stringResource(R.string.rates_loading),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        RateStatus.Failed -> Row(
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.rates_failed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
        }

        RateStatus.Idle -> Text(
            text = stringResource(R.string.rates_source),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    pending?.let { code ->
        val name = SupportedCurrency.fromCode(code)?.let { stringResource(it.nameRes) } ?: code
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(stringResource(R.string.currency_change_title, name)) },
            text = { Text(stringResource(R.string.currency_change_body)) },
            confirmButton = {
                TextButton(onClick = {
                    onConfirm(code)
                    pending = null
                }) { Text(stringResource(R.string.currency_change_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pending = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}
