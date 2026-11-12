package com.example.mymaser.gui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.mymaser.R
import com.example.mymaser.currency.countable
import com.example.mymaser.gui.components.ChipRow
import com.example.mymaser.gui.components.ContentMaxWidth
import com.example.mymaser.gui.components.EditEntryDialog
import com.example.mymaser.gui.components.EmptyState
import com.example.mymaser.gui.components.HistoryItem
import com.example.mymaser.gui.components.HistorySummarySheet
import com.example.mymaser.gui.components.monthYearLabel
import com.example.mymaser.gui.LocalDisplayCurrency
import com.example.mymaser.gui.formatSignedCurrency
import com.example.mymaser.gui.theme.MaserPreview
import com.example.mymaser.gui.theme.ScreenPreviews
import com.example.mymaser.gui.theme.maserColors
import com.example.mymaser.gui.theme.tabular
import androidx.compose.runtime.LaunchedEffect
import com.example.mymaser.history.AttachmentFilter
import com.example.mymaser.history.History
import com.example.mymaser.history.HistoryPeriod
import com.example.mymaser.history.HistoryQuery
import com.example.mymaser.history.SummaryPeriod
import com.example.mymaser.history.TimeFilter
import com.example.mymaser.history.TypeFilter
import com.example.mymaser.history.filterHistory
import com.example.mymaser.history.groupByPeriod
import com.example.mymaser.history.localDate
import java.time.YearMonth
import java.time.ZonedDateTime
import java.time.temporal.WeekFields
import java.util.Locale

private fun TypeFilter.labelRes() = when (this) {
    TypeFilter.All -> R.string.filter_all
    TypeFilter.Income -> R.string.incomes
    TypeFilter.Donations -> R.string.donations
}

private fun TimeFilter.labelRes() = when (this) {
    TimeFilter.AllTime -> R.string.filter_all_time
    TimeFilter.Today -> R.string.filter_today
    TimeFilter.ThisWeek -> R.string.filter_this_week
    TimeFilter.ThisMonth -> R.string.filter_this_month
    TimeFilter.ThisYear -> R.string.filter_this_year
    TimeFilter.LastYear -> R.string.filter_last_year
    TimeFilter.SpecificMonth -> R.string.filter_specific_month
    TimeFilter.SpecificYear -> R.string.filter_specific_year
}

/** Last 12 months has no matching History filter, so it widens to all time. */
private fun SummaryPeriod.toTimeFilter() = when (this) {
    SummaryPeriod.ThisMonth -> TimeFilter.ThisMonth
    SummaryPeriod.ThisYear -> TimeFilter.ThisYear
    SummaryPeriod.LastYear -> TimeFilter.LastYear
    SummaryPeriod.Last12Months, SummaryPeriod.AllTime -> TimeFilter.AllTime
}

@Composable
fun HistoryScreen(
    history: List<History>?,
    onEdit: (History, String, Float) -> Unit,
    onDelete: (History) -> Unit,
    onAttachmentSaved: (History, String) -> Unit,
    onAddEntry: () -> Unit,
    originalOf: (History) -> History = { it },
    notice: @Composable () -> Unit = {},
    showMissingReceipts: Boolean = false,
    onMissingReceiptsShown: () -> Unit = {}
) {
    if (history == null) return

    var searchText by rememberSaveable { mutableStateOf("") }
    var typeFilter by rememberSaveable { mutableStateOf(TypeFilter.All) }
    var attachmentFilter by rememberSaveable { mutableStateOf(AttachmentFilter.Any) }
    var timeFilter by rememberSaveable { mutableStateOf(TimeFilter.AllTime) }
    var monthText by rememberSaveable { mutableStateOf<String?>(null) }
    var year by rememberSaveable { mutableStateOf<Int?>(null) }

    var selectedId by rememberSaveable { mutableStateOf<Int?>(null) }
    var editingId by rememberSaveable { mutableStateOf<Int?>(null) }
    val selected = history.firstOrNull { it.id == selectedId }
    val editing = history.firstOrNull { it.id == editingId }

    val query = HistoryQuery(
        text = searchText,
        type = typeFilter,
        attachment = attachmentFilter,
        time = timeFilter,
        month = monthText?.let(YearMonth::parse),
        year = year
    )

    fun clearFilters() {
        searchText = ""
        typeFilter = TypeFilter.All
        attachmentFilter = AttachmentFilter.Any
        timeFilter = TimeFilter.AllTime
        monthText = null
        year = null
    }

    LaunchedEffect(showMissingReceipts) {
        if (!showMissingReceipts) return@LaunchedEffect
        clearFilters()
        typeFilter = TypeFilter.Donations
        attachmentFilter = AttachmentFilter.Without
        timeFilter = TimeFilter.ThisYear
        onMissingReceiptsShown()
    }

    val now = remember(history) { ZonedDateTime.now() }
    val firstDayOfWeek = remember { WeekFields.of(Locale.getDefault()).firstDayOfWeek }
    val filtered = remember(history, query, now) {
        filterHistory(history, query, now, firstDayOfWeek)
    }
    val groups = remember(filtered, now) { groupByPeriod(filtered, now, firstDayOfWeek) }
    val availableMonths = remember(history) {
        history.map { YearMonth.from(it.localDate(now.zone)) }
            .distinct()
            .sortedDescending()
    }
    val availableYears = remember(availableMonths) {
        availableMonths.map { it.year }.distinct()
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
            SearchField(value = searchText, onValueChange = { searchText = it })
            Box(Modifier.padding(horizontal = 16.dp)) { notice() }

            ChipRow(contentPadding = 16.dp) {
                TimeFilterChip(
                    query = query,
                    availableMonths = availableMonths,
                    availableYears = availableYears,
                    onSelect = { time, month, selectedYear ->
                        timeFilter = time
                        monthText = month?.toString()
                        year = selectedYear
                    }
                )
                TypeFilter.entries.forEach { option ->
                    FilterChip(
                        selected = typeFilter == option,
                        onClick = { typeFilter = option },
                        label = { Text(stringResource(option.labelRes())) }
                    )
                }
                AttachmentFilterChip(selected = attachmentFilter, onSelect = { attachmentFilter = it })
            }

            if (query.isActive) {
                ResultsSummary(entries = filtered, onClear = ::clearFilters)
            }

            if (groups.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        icon = Icons.Outlined.SearchOff,
                        message = stringResource(R.string.no_matching_entries)
                    ) {
                        TextButton(onClick = ::clearFilters) {
                            Text(stringResource(R.string.clear_filters))
                        }
                    }
                }
            } else {
                HistoryList(
                    groups = groups,
                    onClick = { selectedId = it.id },
                    onAttachmentSaved = onAttachmentSaved,
                    originalOf = originalOf
                )
            }
        }
    }

    selected?.let { entry ->
        HistorySummarySheet(
            history = entry,
            allHistory = history,
            originalOf = originalOf,
            onEdit = { editingId = entry.id },
            onDelete = {
                selectedId = null
                onDelete(entry)
            },
            onShowAll = { period ->
                selectedId = null
                clearFilters()
                searchText = entry.name.trim()
                typeFilter = if (entry.isDonation) TypeFilter.Donations else TypeFilter.Income
                timeFilter = period.toTimeFilter()
            },
            onDismiss = { selectedId = null }
        )
    }

    editing?.let { entry ->
        EditEntryDialog(
            entry = originalOf(entry),
            onConfirm = { name, amount ->
                onEdit(entry, name, amount)
                editingId = null
            },
            onDismiss = { editingId = null }
        )
    }
}

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(stringResource(R.string.search_hint)) },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        trailingIcon = if (value.isNotEmpty()) {
            {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(
                        Icons.Filled.Clear,
                        contentDescription = stringResource(R.string.clear_search)
                    )
                }
            }
        } else {
            null
        },
        singleLine = true,
        shape = MaterialTheme.shapes.extraLarge,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

private enum class TimeMenuLevel { Root, Months, Years }

@Composable
private fun TimeFilterChip(
    query: HistoryQuery,
    availableMonths: List<YearMonth>,
    availableYears: List<Int>,
    onSelect: (TimeFilter, YearMonth?, Int?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var level by remember { mutableStateOf(TimeMenuLevel.Root) }
    val label = when {
        query.time == TimeFilter.SpecificMonth && query.month != null -> query.month.monthYearLabel()
        query.time == TimeFilter.SpecificYear && query.year != null -> query.year.toString()
        else -> stringResource(query.time.labelRes())
    }

    fun choose(time: TimeFilter, month: YearMonth? = null, year: Int? = null) {
        onSelect(time, month, year)
        expanded = false
    }

    Box {
        FilterChip(
            selected = query.time != TimeFilter.AllTime,
            onClick = {
                level = TimeMenuLevel.Root
                expanded = true
            },
            label = { Text(label) },
            trailingIcon = {
                Icon(
                    Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                )
            }
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (level != TimeMenuLevel.Root) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.back)) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) },
                    onClick = { level = TimeMenuLevel.Root }
                )
            }
            when (level) {
                TimeMenuLevel.Root -> TimeFilter.entries.forEach { option ->
                    MenuOption(
                        text = stringResource(option.labelRes()),
                        selected = query.time == option,
                        onClick = {
                            when (option) {
                                TimeFilter.SpecificMonth -> level = TimeMenuLevel.Months
                                TimeFilter.SpecificYear -> level = TimeMenuLevel.Years
                                else -> choose(option)
                            }
                        }
                    )
                }

                TimeMenuLevel.Months -> availableMonths.forEach { month ->
                    MenuOption(
                        text = month.monthYearLabel(),
                        selected = query.time == TimeFilter.SpecificMonth && query.month == month,
                        onClick = { choose(TimeFilter.SpecificMonth, month = month) }
                    )
                }

                TimeMenuLevel.Years -> availableYears.forEach { year ->
                    MenuOption(
                        text = year.toString(),
                        selected = query.time == TimeFilter.SpecificYear && query.year == year,
                        onClick = { choose(TimeFilter.SpecificYear, year = year) }
                    )
                }
            }
        }
    }
}

private fun AttachmentFilter.labelRes() = when (this) {
    AttachmentFilter.Any -> R.string.filter_receipts
    AttachmentFilter.With -> R.string.filter_with_attachments
    AttachmentFilter.Without -> R.string.filter_without_attachments
}

@Composable
private fun AttachmentFilterChip(selected: AttachmentFilter, onSelect: (AttachmentFilter) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = selected != AttachmentFilter.Any,
            onClick = { expanded = true },
            label = { Text(stringResource(selected.labelRes())) },
            trailingIcon = {
                Icon(
                    Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                )
            }
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            AttachmentFilter.entries.forEach { option ->
                MenuOption(
                    text = stringResource(
                        if (option == AttachmentFilter.Any) R.string.filter_all else option.labelRes()
                    ),
                    selected = selected == option,
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun MenuOption(text: String, selected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text) },
        onClick = onClick,
        trailingIcon = if (selected) {
            { Icon(Icons.Filled.Check, contentDescription = null) }
        } else {
            null
        }
    )
}

/** Income and donation totals in the display currency; entries awaiting a rate are left out. */
@Composable
private fun rememberTotals(entries: List<History>): Pair<Double, Double> {
    val currency = LocalDisplayCurrency.current
    return remember(entries, currency) {
        val countable = entries.countable(currency)
        countable.filter { !it.isDonation }.sumOf { it.amount.toDouble() } to
            countable.filter { it.isDonation }.sumOf { it.amount.toDouble() }
    }
}

@Composable
private fun ResultsSummary(entries: List<History>, onClear: () -> Unit) {
    val (incomeTotal, donationTotal) = rememberTotals(entries)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = pluralStringResource(R.plurals.results_count, entries.size, entries.size),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        AmountTotals(incomeTotal, donationTotal, Modifier.weight(1f))
        TextButton(onClick = onClear) {
            Text(stringResource(R.string.clear_filters))
        }
    }
}

@Composable
private fun AmountTotals(incomeTotal: Double, donationTotal: Double, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
    ) {
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

@Composable
private fun HistoryList(
    groups: List<Pair<HistoryPeriod, List<History>>>,
    onClick: (History) -> Unit,
    onAttachmentSaved: (History, String) -> Unit,
    originalOf: (History) -> History
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
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    original = originalOf(entry)
                )
            }
        }
    }
}

@Composable
private fun PeriodHeader(period: HistoryPeriod, entries: List<History>) {
    val (incomeTotal, donationTotal) = rememberTotals(entries)

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
                modifier = Modifier.semantics { heading() }
            )
            AmountTotals(incomeTotal, donationTotal, Modifier.weight(1f))
        }
    }
}

@Composable
private fun periodLabel(period: HistoryPeriod): String = when (period) {
    HistoryPeriod.Today -> stringResource(R.string.today)
    HistoryPeriod.ThisWeek -> stringResource(R.string.this_week)
    HistoryPeriod.ThisMonth -> stringResource(R.string.this_month)
    is HistoryPeriod.Month -> remember(period) { period.month.monthYearLabel() }
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
            onEdit = { _, _, _ -> },
            onDelete = {},
            onAttachmentSaved = { _, _ -> },
            onAddEntry = {}
        )
    }
}
