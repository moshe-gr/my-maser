package com.example.mymaser.gui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.mymaser.R
import com.example.mymaser.gui.formatCurrency
import com.example.mymaser.history.EntryWarning
import java.util.Date

/** Asks before saving an entry that looks like a duplicate or a typo; never blocks saving. */
@Composable
fun EntryWarningDialog(
    warning: EntryWarning,
    name: String,
    amount: String,
    onSaveAnyway: () -> Unit,
    onFix: () -> Unit
) {
    val dateFormat = rememberDateTimeFormat()
    val (title, body) = when (warning) {
        is EntryWarning.Duplicate -> stringResource(R.string.guard_duplicate_title) to stringResource(
            R.string.guard_duplicate_body,
            amount,
            name,
            dateFormat.format(Date(warning.existing.timeStamp))
        )
        is EntryWarning.UnusualAmount -> stringResource(R.string.guard_unusual_title) to stringResource(
            R.string.guard_unusual_body,
            amount,
            formatCurrency(warning.usual.toDouble(), currencyCode = warning.currency),
            name
        )
    }
    AlertDialog(
        onDismissRequest = onFix,
        icon = { Icon(Icons.Outlined.WarningAmber, contentDescription = null) },
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onSaveAnyway) { Text(stringResource(R.string.guard_save_anyway)) } },
        dismissButton = { TextButton(onClick = onFix) { Text(stringResource(R.string.guard_fix)) } }
    )
}
