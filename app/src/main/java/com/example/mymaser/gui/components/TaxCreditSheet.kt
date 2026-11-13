package com.example.mymaser.gui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.mymaser.R
import com.example.mymaser.currency.SupportedCurrency
import com.example.mymaser.gui.formatCurrency
import com.example.mymaser.gui.theme.tabular
import com.example.mymaser.history.TaxYearCredit

@Composable
private fun shekels(amount: Double) =
    formatCurrency(amount, trimWholeDecimals = true, currencyCode = SupportedCurrency.ILS.code)

/** Section 46 estimates for this year and the past years that can still be claimed. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaxCreditSheet(
    years: List<TaxYearCredit>,
    claimed: Set<Int>,
    onClaimedChange: (year: Int, claimed: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(stringResource(R.string.tax_credit_title), style = MaterialTheme.typography.titleLarge)

            if (years.none { it.eligible > 0 }) {
                Note(stringResource(R.string.tax_credit_none))
            }

            years.firstOrNull()?.let { ThisYear(it) }

            val past = years.drop(1).filter { it.eligible > 0 }
            if (past.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    stringResource(R.string.tax_credit_past_years),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.semantics { heading() }
                )
                past.forEach { year ->
                    PastYearRow(year, year.year in claimed) { onClaimedChange(year.year, it) }
                }
                val open = past.filter { it.year !in claimed }.sumOf { it.credit }
                if (open > 0) {
                    Text(
                        stringResource(R.string.tax_credit_open_total, shekels(open)),
                        style = MaterialTheme.typography.bodyLarge.tabular
                    )
                }
                Note(stringResource(R.string.tax_credit_claim_hint))
            }

            Note(stringResource(R.string.tax_credit_estimate_note))
        }
    }
}

@Composable
private fun ThisYear(year: TaxYearCredit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(R.string.tax_credit_this_year, year.year),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.semantics { heading() }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
                label = stringResource(R.string.tax_credit_eligible),
                value = shekels(year.eligible),
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = stringResource(R.string.tax_credit_estimate),
                value = shekels(year.credit),
                modifier = Modifier.weight(1f)
            )
        }
        if (!year.reachedMinimum) {
            Text(
                stringResource(R.string.tax_credit_min_progress, shekels(year.eligible), shekels(year.minimum)),
                style = MaterialTheme.typography.bodyMedium.tabular
            )
            LinearProgressIndicator(
                progress = { (year.eligible / year.minimum).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (year.overCap > 0) {
            Text(
                stringResource(R.string.tax_credit_over_cap, shekels(year.overCap)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun PastYearRow(year: TaxYearCredit, claimed: Boolean, onClaimedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = claimed, role = Role.Checkbox, onValueChange = onClaimedChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(year.year.toString(), style = MaterialTheme.typography.titleMedium)
                if (!claimed && year.credit > 0) OpenBadge()
            }
            Text(
                if (year.reachedMinimum) {
                    stringResource(R.string.tax_credit_year_row, shekels(year.eligible), shekels(year.credit))
                } else {
                    stringResource(R.string.tax_credit_year_below_minimum, shekels(year.eligible))
                },
                style = MaterialTheme.typography.bodyMedium.tabular,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(stringResource(R.string.tax_credit_claimed), style = MaterialTheme.typography.labelLarge)
        Checkbox(checked = claimed, onCheckedChange = null)
    }
}

@Composable
private fun OpenBadge() {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = MaterialTheme.shapes.extraSmall
    ) {
        Text(
            stringResource(R.string.tax_credit_open_badge),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
