package com.example.mymaser.gui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.mymaser.R
import com.example.mymaser.ai.AiError
import com.example.mymaser.ai.ExtractedEntry
import com.example.mymaser.ai.ImportState
import com.example.mymaser.ai.ReviewEntry
import com.example.mymaser.ai.canRetry
import com.example.mymaser.ai.documentTimeStamp
import com.example.mymaser.currency.SupportedCurrency
import com.example.mymaser.gui.formatCurrency
import com.example.mymaser.history.EntryWarning
import com.example.mymaser.history.NewEntry
import com.example.mymaser.history.formatAmountInput
import com.example.mymaser.history.sanitizeAmountInput
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Date

private data class ReviewRow(
    val item: ReviewEntry,
    val selected: Boolean = item.loggedAs == null && !item.repeat,
    val name: String = item.entry.name,
    val amountText: String = formatAmountInput(item.entry.amount),
    val taxCredit: Boolean = item.entry.taxCredit
) {
    val entry: ExtractedEntry get() = item.entry

    fun toNewEntry(nowMillis: Long): NewEntry? {
        val amount = amountText.toFloatOrNull()?.takeIf { it > 0F } ?: return null
        val trimmed = name.trim().takeIf { it.isNotEmpty() } ?: return null
        return NewEntry(
            name = trimmed,
            amount = amount,
            currency = entry.currency,
            isDonation = entry.isDonation,
            timeStamp = documentTimeStamp(entry.date, ZoneId.systemDefault(), nowMillis),
            documentName = item.documentName,
            taxCredit = entry.isDonation && taxCredit
        )
    }
}

/** Shows what's happening with a document import, and lets the user confirm what to save. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportReviewSheet(
    state: ImportState,
    onCheck: (NewEntry) -> EntryWarning?,
    onConfirm: (List<NewEntry>) -> Unit,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    if (state == ImportState.Idle) return
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberSheetState()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (state) {
                ImportState.Idle -> Unit
                ImportState.Reading -> Reading()
                is ImportState.Empty -> Message(
                    text = stringResource(R.string.import_nothing_found).takeIf { state.locked == 0 },
                    skipped = state.skipped,
                    locked = state.locked,
                    onDismiss = onDismiss
                )
                is ImportState.Failed -> Failure(state.error, onRetry, onOpenSettings, onDismiss)
                is ImportState.Review -> Review(state, onCheck, onConfirm, onDismiss)
            }
        }
    }
}

@Composable
private fun Reading() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp))
        Text(stringResource(R.string.import_reading), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ColumnScope.Message(text: String?, skipped: Int, locked: Int, onDismiss: () -> Unit) {
    if (text != null) Text(text, style = MaterialTheme.typography.bodyLarge)
    LockedNote(locked, prominent = text == null)
    SkippedNote(skipped)
    TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
        Text(stringResource(R.string.close))
    }
}

@Composable
private fun LockedNote(locked: Int, prominent: Boolean = false) {
    if (locked > 0) {
        Text(
            pluralStringResource(R.plurals.import_locked, locked, locked),
            style = if (prominent) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodySmall,
            color = if (prominent) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun SkippedNote(skipped: Int) {
    if (skipped > 0) {
        Text(
            pluralStringResource(R.plurals.import_skipped, skipped, skipped),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ColumnScope.Failure(
    error: AiError,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val message = when (error) {
        AiError.Disabled -> R.string.import_error_disabled
        AiError.NotConfigured -> R.string.import_error_not_configured
        AiError.QuotaExceeded -> R.string.import_error_quota
        AiError.Network -> R.string.import_error_network
        AiError.Busy -> R.string.ai_error_busy
        AiError.Failed -> R.string.import_error_failed
    }
    Text(stringResource(message), style = MaterialTheme.typography.bodyLarge)
    Row(modifier = Modifier.align(Alignment.End)) {
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        when {
            error == AiError.Disabled ->
                Button(onClick = onOpenSettings) { Text(stringResource(R.string.open_settings)) }
            error.canRetry ->
                Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
        }
    }
}

@Composable
private fun ColumnScope.Review(
    state: ImportState.Review,
    onCheck: (NewEntry) -> EntryWarning?,
    onConfirm: (List<NewEntry>) -> Unit,
    onDismiss: () -> Unit
) {
    val rows = remember(state) { mutableStateListOf(*state.entries.map { ReviewRow(it) }.toTypedArray()) }
    val nowMillis = remember(state) { System.currentTimeMillis() }
    val entries = rows.map { it.toNewEntry(nowMillis) }
    val chosen = rows.indices.filter { rows[it].selected }.map { entries[it] }
    val allChosenValid = chosen.none { it == null }

    Text(stringResource(R.string.import_review_title), style = MaterialTheme.typography.titleLarge)
    LockedNote(state.locked)
    SkippedNote(state.skipped)
    Column(
        modifier = Modifier
            .weight(1f, fill = false)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        rows.forEachIndexed { index, row ->
            ReviewRowCard(
                row = row,
                warning = remember(entries[index]) {
                    entries[index]?.takeIf { row.item.loggedAs == null }?.let(onCheck)
                },
                onChange = { rows[index] = it }
            )
        }
    }
    Row(modifier = Modifier.align(Alignment.End)) {
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        Button(
            onClick = { onConfirm(chosen.filterNotNull()) },
            enabled = chosen.isNotEmpty() && allChosenValid
        ) {
            Text(stringResource(R.string.import_add_selected))
        }
    }
}

@Composable
private fun ReviewRowCard(row: ReviewRow, warning: EntryWarning?, onChange: (ReviewRow) -> Unit) {
    val dateText = row.entry.date?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    val kind = stringResource(if (row.entry.isDonation) R.string.donation else R.string.income)
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.Top) {
            Checkbox(checked = row.selected, onCheckedChange = { onChange(row.copy(selected = it)) })
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    listOfNotNull(kind, dateText).joinToString(" · "),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                OutlinedTextField(
                    value = row.name,
                    onValueChange = { onChange(row.copy(name = it)) },
                    label = {
                        Text(stringResource(if (row.entry.isDonation) R.string.donate_to else R.string.source_of_income))
                    },
                    enabled = row.selected,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = row.amountText,
                    onValueChange = { input -> sanitizeAmountInput(input)?.let { onChange(row.copy(amountText = it)) } },
                    label = { Text(stringResource(R.string.amount)) },
                    suffix = { Text(SupportedCurrency.symbolFor(row.entry.currency)) },
                    enabled = row.selected,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                if (row.entry.isDonation && row.selected) {
                    TaxCreditSwitch(checked = row.taxCredit, onChange = { onChange(row.copy(taxCredit = it)) })
                }
                if (row.item.documentName.trim() != row.name.trim()) {
                    Note(stringResource(R.string.import_document_name, row.item.documentName))
                }
                row.item.loggedAs?.let { logged ->
                    Note(
                        stringResource(
                            R.string.import_already_logged,
                            rememberDateFormat().format(Date(logged.timeStamp)),
                            logged.name
                        )
                    )
                }
                if (row.item.repeat) Note(stringResource(R.string.import_repeat))
                if (row.selected && warning != null) WarningText(warning)
            }
        }
    }
}

@Composable
private fun WarningText(warning: EntryWarning) {
    val dateFormat = rememberDateFormat()
    val text = when (warning) {
        is EntryWarning.Duplicate -> stringResource(
            R.string.import_warning_duplicate,
            dateFormat.format(Date(warning.existing.timeStamp))
        )
        is EntryWarning.UnusualAmount -> stringResource(
            R.string.import_warning_unusual,
            formatCurrency(warning.usual.toDouble(), currencyCode = warning.currency)
        )
    }
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
}
