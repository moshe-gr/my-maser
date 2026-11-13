package com.example.mymaser.gui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.mymaser.R
import com.example.mymaser.currency.countable
import com.example.mymaser.gui.LocalDisplayCurrency
import com.example.mymaser.gui.formatCurrency
import com.example.mymaser.gui.formatPercent
import com.example.mymaser.gui.formatSignedCurrency
import com.example.mymaser.gui.theme.maserColors
import com.example.mymaser.gui.theme.tabular
import com.example.mymaser.history.History
import com.example.mymaser.history.NameShare
import com.example.mymaser.history.Overview
import com.example.mymaser.history.SummaryPeriod
import com.example.mymaser.history.computeOverview
import com.example.mymaser.history.relativeChange
import java.time.ZonedDateTime
import kotlin.math.abs

private const val GIVEN_DIGITS = 1

/** Totals for a period across all entries, compared with the period before, with top names. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewSheet(
    history: List<History>,
    onOpenEntry: (id: Int) -> Unit,
    onDismiss: () -> Unit
) {
    val displayCurrency = LocalDisplayCurrency.current
    var period by rememberSaveable { mutableStateOf(SummaryPeriod.ThisYear) }
    val overview = remember(history, period, displayCurrency) {
        computeOverview(history.countable(displayCurrency), period, ZonedDateTime.now())
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                stringResource(R.string.overview_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
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
                OverviewStats(overview, period)
                DualBarChart(
                    months = overview.monthly,
                    title = stringResource(R.string.overview_chart_title),
                    incomeColor = MaterialTheme.maserColors.income,
                    donationColor = MaterialTheme.maserColors.donation
                )
                if (overview.topDonations.isEmpty() && overview.topIncome.isEmpty()) {
                    Text(
                        stringResource(R.string.summary_no_entries_in_period),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TopNames(stringResource(R.string.overview_top_donations), overview.topDonations) {
                    onOpenEntry(it.latestId)
                }
                TopNames(stringResource(R.string.overview_top_income), overview.topIncome) {
                    onOpenEntry(it.latestId)
                }
            }
        }
    }
}

@Composable
private fun OverviewStats(overview: Overview, period: SummaryPeriod) {
    val current = overview.current
    val previous = overview.previous
    val changeRes = period.changeLabelRes()

    @Composable
    fun change(now: Double, before: Double?): String? =
        changeRes?.let { res -> relativeChange(now, before)?.let { stringResource(res, formatPercent(it, signed = true)) } }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
                label = stringResource(R.string.income),
                value = formatCurrency(current.income),
                supporting = change(current.income, previous?.income),
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = stringResource(R.string.donations),
                value = formatCurrency(current.donations),
                supporting = change(current.donations, previous?.donations),
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
                label = stringResource(R.string.overview_given),
                value = current.givenShare?.let { formatPercent(it, maxFractionDigits = GIVEN_DIGITS) } ?: "—",
                supporting = stringResource(R.string.overview_given_value, formatCurrency(current.maserDonations)),
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = stringResource(R.string.overview_maser_change),
                value = formatSignedCurrency(abs(current.maserChange), isNegative = current.maserChange < 0),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun TopNames(title: String, names: List<NameShare>, onClick: (NameShare) -> Unit) {
    if (names.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier
                .padding(vertical = 8.dp)
                .semantics { heading() }
        )
        names.forEach { name ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onClick(name) }
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    name.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    stringResource(R.string.overview_name_value, formatCurrency(name.total), formatPercent(name.share)),
                    style = MaterialTheme.typography.bodyLarge.tabular,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
