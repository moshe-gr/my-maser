package com.example.mymaser.gui.components

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.mymaser.R
import com.example.mymaser.currency.countable
import com.example.mymaser.gui.LocalDisplayCurrency
import com.example.mymaser.gui.formatCurrency
import com.example.mymaser.gui.formatPercent
import com.example.mymaser.gui.theme.maserColors
import com.example.mymaser.gui.theme.tabular
import com.example.mymaser.history.History
import com.example.mymaser.history.NameAnalytics
import com.example.mymaser.history.SummaryPeriod
import com.example.mymaser.history.computeNameAnalytics
import com.example.mymaser.history.entriesToCsv
import com.example.mymaser.history.isTaxOnlyDonation
import com.example.mymaser.history.nameKey
import com.example.mymaser.settings.formatPercentage
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Date
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun SummaryPeriod.labelRes() = when (this) {
    SummaryPeriod.ThisMonth -> R.string.this_month
    SummaryPeriod.ThisYear -> R.string.this_year
    SummaryPeriod.LastYear -> R.string.filter_last_year
    SummaryPeriod.Last12Months -> R.string.summary_last_12_months
    SummaryPeriod.AllTime -> R.string.filter_all_time
}

private fun SummaryPeriod.changeLabelRes() = when (this) {
    SummaryPeriod.ThisMonth -> R.string.summary_change_month
    SummaryPeriod.ThisYear -> R.string.summary_change_ytd
    SummaryPeriod.LastYear -> R.string.summary_change_year
    SummaryPeriod.Last12Months -> R.string.summary_change_12m
    SummaryPeriod.AllTime -> null
}

private fun csvFileName(name: String, period: SummaryPeriod): String {
    val safeName = name.trim().replace(Regex("[^\\p{L}\\p{N}]+"), "-").trim('-').ifEmpty { "entries" }
    return "$safeName-${period.name}.csv"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistorySummarySheet(
    history: History,
    allHistory: List<History>,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onShowAll: (SummaryPeriod) -> Unit,
    onDismiss: () -> Unit,
    originalOf: (History) -> History = { it },
    onTaxCreditForName: (Boolean) -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val displayCurrency = LocalDisplayCurrency.current
    var period by rememberSaveable(history.id) { mutableStateOf(SummaryPeriod.ThisYear) }
    val analytics = remember(allHistory, history, period, displayCurrency) {
        computeNameAnalytics(
            allHistory.countable(displayCurrency),
            history,
            period,
            ZonedDateTime.now()
        )
    }

    fun hideThen(action: () -> Unit) {
        scope.launch { sheetState.hide() }.invokeOnCompletion { action() }
    }

    val exportedMessage = pluralStringResource(
        R.plurals.summary_exported,
        analytics.count,
        analytics.count
    )
    val exportFailedMessage = stringResource(R.string.summary_export_failed)
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val csv = entriesToCsv(analytics.entries, ZoneId.systemDefault(), originalOf)
        scope.launch {
            val written = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use {
                        it.write(csv.toByteArray(Charsets.UTF_8))
                    } != null
                }.getOrDefault(false)
            }
            Toast.makeText(
                context,
                if (written) exportedMessage else exportFailedMessage,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SheetHeader(history, Modifier.padding(horizontal = 24.dp))

            ChipRow(contentPadding = 24.dp) {
                SummaryPeriod.entries.forEach { option ->
                    FilterChip(
                        selected = period == option,
                        onClick = { period = option },
                        label = { Text(stringResource(option.labelRes())) }
                    )
                }
            }

            Column(
                modifier = Modifier.padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StatGrid(history, analytics, period)

                analytics.recurrence?.let { recurrence ->
                    val nextDate = recurrence.nextExpected.format(
                        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                    )
                    InfoLine(
                        icon = Icons.Outlined.EventRepeat,
                        text = stringResource(
                            R.string.summary_recurring,
                            recurrence.typicalDay,
                            nextDate
                        )
                    )
                }

                MonthlyBarChart(
                    months = analytics.monthly,
                    color = if (history.isDonation) {
                        MaterialTheme.maserColors.donation
                    } else {
                        MaterialTheme.maserColors.income
                    }
                )

                DetailRows(history, analytics)

                if (history.isDonation) {
                    val allEligible = remember(allHistory, history) {
                        val key = nameKey(history.name)
                        allHistory.filter { it.isDonation && nameKey(it.name) == key }
                            .all { it.taxCredit }
                    }
                    TaxCreditSwitch(
                        checked = allEligible,
                        onChange = onTaxCreditForName,
                        label = stringResource(R.string.tax_credit_for_name, history.name.trim())
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilledTonalButton(
                        onClick = { hideThen { onShowAll(period) } },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.List, null, Modifier.size(18.dp))
                        Text(stringResource(R.string.summary_show_all), Modifier.padding(start = 8.dp))
                    }
                    OutlinedButton(
                        onClick = { exportLauncher.launch(csvFileName(history.name, period)) },
                        enabled = analytics.count > 0,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Icon(Icons.Outlined.FileDownload, null, Modifier.size(18.dp))
                        Text(stringResource(R.string.summary_export), Modifier.padding(start = 8.dp))
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = onEdit,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Icon(Icons.Outlined.Edit, null, Modifier.size(18.dp))
                        Text(stringResource(R.string.edit), Modifier.padding(start = 8.dp))
                    }
                    OutlinedButton(
                        onClick = { hideThen(onDelete) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                        shape = MaterialTheme.shapes.small,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Outlined.Delete, null, Modifier.size(18.dp))
                        Text(stringResource(R.string.delete), Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetHeader(history: History, modifier: Modifier = Modifier) {
    val dateFormat = rememberDateFormat()
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        EntryTypeBadge(history.isDonation, size = 48.dp, iconSize = 24.dp)
        Column {
            Text(
                text = history.name,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(
                    R.string.summary_entry_subtitle,
                    formatCurrency(history.amount.toDouble(), currencyCode = history.currency),
                    dateFormat.format(Date(history.timeStamp))
                ),
                style = MaterialTheme.typography.bodyMedium.tabular,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            DonationTags(history, Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun StatGrid(history: History, analytics: NameAnalytics, period: SummaryPeriod) {
    val changeText = analytics.change?.let { change ->
        period.changeLabelRes()?.let { stringResource(it, formatPercent(change, signed = true)) }
    }
    val shareText = analytics.shareOfType?.let {
        stringResource(
            if (history.isDonation) R.string.summary_share_donation else R.string.summary_share_income,
            formatPercent(it)
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
                label = stringResource(R.string.total),
                value = formatCurrency(analytics.total),
                supporting = changeText,
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = stringResource(R.string.entries_count),
                value = analytics.count.toString(),
                supporting = shareText,
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
                label = stringResource(R.string.average),
                value = if (analytics.count > 0) formatCurrency(analytics.average) else EMPTY_VALUE,
                modifier = Modifier.weight(1f)
            )
            if (history.isTaxOnlyDonation) {
                StatTile(
                    label = stringResource(R.string.summary_not_counted),
                    value = formatCurrency(
                        analytics.entries.filter { it.isTaxOnly }.sumOf { it.amount.toDouble() }
                    ),
                    modifier = Modifier.weight(1f)
                )
            } else {
                StatTile(
                    label = stringResource(
                        if (history.isDonation) R.string.summary_maser_reduced else R.string.summary_added_to_maser
                    ),
                    value = formatCurrency(abs(analytics.maserEffect)),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

private const val EMPTY_VALUE = "—"

@Composable
private fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    supporting: String? = null
) {
    Card(
        modifier = modifier.semantics(mergeDescendants = true) {},
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(text = value, style = MaterialTheme.typography.titleLarge.tabular, maxLines = 1)
            if (supporting != null) {
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun InfoLine(icon: ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DetailRows(history: History, analytics: NameAnalytics) {
    val dateFormat = rememberDateFormat()
    fun date(time: Long?) = time?.let { dateFormat.format(Date(it)) } ?: EMPTY_VALUE

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        if (analytics.count == 0) {
            Text(
                text = stringResource(R.string.summary_no_entries_in_period),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return
        }
        SummaryRow(
            stringResource(R.string.summary_highest),
            analytics.highest?.let { formatCurrency(it) } ?: EMPTY_VALUE
        )
        SummaryRow(
            stringResource(R.string.summary_lowest),
            analytics.lowest?.let { formatCurrency(it) } ?: EMPTY_VALUE
        )
        SummaryRow(stringResource(R.string.summary_first), date(analytics.firstTime))
        SummaryRow(stringResource(R.string.summary_last), date(analytics.lastTime))
        if (!history.isDonation) {
            SummaryRow(
                stringResource(R.string.summary_entry_percentage),
                formatPercentage(history.maserPercentage)
            )
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    LabelValueRow(label, value, MaterialTheme.typography.bodyLarge.tabular)
}
