package com.example.mymaser.gui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.mymaser.R
import com.example.mymaser.gui.components.ContentMaxWidth
import com.example.mymaser.gui.components.EditEntryDialog
import com.example.mymaser.gui.components.EmptyState
import com.example.mymaser.gui.components.HistoryItem
import com.example.mymaser.gui.components.HistorySummarySheet
import com.example.mymaser.gui.formatSignedCurrency
import com.example.mymaser.gui.theme.MaserPreview
import com.example.mymaser.gui.theme.ScreenPreviews
import com.example.mymaser.gui.theme.maserColors
import com.example.mymaser.gui.theme.tabular
import com.example.mymaser.history.History
import com.example.mymaser.history.HistoryPeriod
import com.example.mymaser.history.HistoryRepository
import com.example.mymaser.history.groupByPeriod
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale

private enum class HistoryFilter(val label: Int) {
    All(R.string.filter_all),
    Income(R.string.incomes),
    Donations(R.string.donations);

    fun matches(entry: History) = when (this) {
        All -> true
        Income -> !entry.isDonation
        Donations -> entry.isDonation
    }
}

@Composable
fun HistoryScreen(
    history: List<History>?,
    loadSummary: suspend (History) -> HistoryRepository.HistorySummary,
    onEdit: (History, String, Float) -> Unit,
    onDelete: (History) -> Unit,
    onAttachmentSaved: (History, String) -> Unit,
    onAddEntry: () -> Unit
) {
    if (history == null) return

    var filter by rememberSaveable { mutableStateOf(HistoryFilter.All) }
    var selectedId by rememberSaveable { mutableStateOf<Int?>(null) }
    var editingId by rememberSaveable { mutableStateOf<Int?>(null) }
    val selected = history.firstOrNull { it.id == selectedId }
    val editing = history.firstOrNull { it.id == editingId }

    val groups = remember(history, filter) {
        groupByPeriod(
            history = history.filter(filter::matches),
            now = ZonedDateTime.now(),
            firstDayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek
        )
    }

    if (history.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(
                icon = Icons.Outlined.History,
                message = stringResource(R.string.no_history_found)
            ) {
                FilledTonalButton(onClick = onAddEntry) {
                    Text(stringResource(R.string.add_income))
                }
            }
        }
        return
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = ContentMaxWidth)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HistoryFilter.entries.forEach { option ->
                    FilterChip(
                        selected = filter == option,
                        onClick = { filter = option },
                        label = { Text(stringResource(option.label)) }
                    )
                }
            }
            if (groups.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        icon = Icons.Outlined.History,
                        message = stringResource(R.string.no_history_found)
                    )
                }
            } else {
                HistoryList(
                    groups = groups,
                    onClick = { selectedId = it.id },
                    onAttachmentSaved = onAttachmentSaved
                )
            }
        }
    }

    selected?.let { entry ->
        HistorySummarySheet(
            history = entry,
            loadSummary = loadSummary,
            onEdit = { editingId = entry.id },
            onDelete = {
                selectedId = null
                onDelete(entry)
            },
            onDismiss = { selectedId = null }
        )
    }

    editing?.let { entry ->
        EditEntryDialog(
            entry = entry,
            onConfirm = { name, amount ->
                onEdit(entry, name, amount)
                editingId = null
            },
            onDismiss = { editingId = null }
        )
    }
}

@Composable
private fun HistoryList(
    groups: List<Pair<HistoryPeriod, List<History>>>,
    onClick: (History) -> Unit,
    onAttachmentSaved: (History, String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        groups.forEach { (period, entries) ->
            stickyHeader(key = "header-$period") {
                PeriodHeader(period, entries)
            }
            items(entries, key = { it.id }) { entry ->
                HistoryItem(
                    history = entry,
                    onClick = { onClick(entry) },
                    onAttachmentSaved = { onAttachmentSaved(entry, it) },
                    modifier = Modifier
                        .animateItem()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun PeriodHeader(period: HistoryPeriod, entries: List<History>) {
    val incomeTotal = remember(entries) {
        entries.filter { !it.isDonation }.sumOf { it.amount.toDouble() }
    }
    val donationTotal = remember(entries) {
        entries.filter { it.isDonation }.sumOf { it.amount.toDouble() }
    }

    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = periodLabel(period),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() }
            )
            if (incomeTotal > 0) {
                Text(
                    text = formatSignedCurrency(incomeTotal, isNegative = false),
                    style = MaterialTheme.typography.labelMedium.tabular,
                    color = MaterialTheme.maserColors.income
                )
            }
            if (donationTotal > 0) {
                Text(
                    text = formatSignedCurrency(donationTotal, isNegative = true),
                    style = MaterialTheme.typography.labelMedium.tabular,
                    color = MaterialTheme.maserColors.donation
                )
            }
        }
    }
}

@Composable
private fun periodLabel(period: HistoryPeriod): String = when (period) {
    HistoryPeriod.Today -> stringResource(R.string.today)
    HistoryPeriod.ThisWeek -> stringResource(R.string.this_week)
    HistoryPeriod.ThisMonth -> stringResource(R.string.this_monthh)
    is HistoryPeriod.Month -> remember(period) {
        period.month.format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.getDefault()))
    }
}

private val sampleHistory = listOf(
    History(1, System.currentTimeMillis(), "Salary", 12000F, false),
    History(2, System.currentTimeMillis(), "Local charity", 180F, true, "content://receipt")
)

@ScreenPreviews
@Composable
private fun HistoryPreview() {
    MaserPreview {
        HistoryScreen(
            history = sampleHistory,
            loadSummary = { HistoryRepository.HistorySummary(0.0, 0, 0.0, 0.0, 0.0, 0.0) },
            onEdit = { _, _, _ -> },
            onDelete = {},
            onAttachmentSaved = { _, _ -> },
            onAddEntry = {}
        )
    }
}
