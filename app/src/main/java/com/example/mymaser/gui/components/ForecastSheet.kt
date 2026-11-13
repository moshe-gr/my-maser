package com.example.mymaser.gui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.mymaser.R
import com.example.mymaser.currency.SupportedCurrency
import com.example.mymaser.gui.formatCurrency
import com.example.mymaser.gui.formatSignedCurrency
import com.example.mymaser.gui.theme.maserColors
import com.example.mymaser.gui.theme.tabular
import com.example.mymaser.history.MonthTotal
import com.example.mymaser.history.SourceProjection
import com.example.mymaser.history.TaxYearCredit
import com.example.mymaser.history.YearEndForecast
import com.example.mymaser.history.YearPace
import kotlin.math.abs

/** The forecast broken down: balance by month, the recurring sources behind it, pace and tax credit. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForecastSheet(
    forecast: YearEndForecast?,
    pace: YearPace?,
    taxCredit: TaxYearCredit?,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberSheetState()
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
            Text(stringResource(R.string.forecast_title), style = MaterialTheme.typography.titleLarge)

            if (forecast != null) {
                Text(forecastText(forecast), style = MaterialTheme.typography.bodyLarge)
                if (forecast.months.any { it.balance > 0 }) {
                    MonthlyBarChart(
                        months = forecast.months.map { MonthTotal(it.month, it.balance) },
                        color = MaterialTheme.colorScheme.primary,
                        title = stringResource(R.string.forecast_chart_title),
                        initialSelection = forecast.months.lastIndex
                    )
                }
                val sources = forecast.sources.filter { it.delta != 0.0 }
                if (sources.isNotEmpty()) {
                    SectionTitle(stringResource(R.string.forecast_sources_title))
                    sources.forEach { SourceRow(it) }
                }
            } else {
                Text(
                    stringResource(R.string.forecast_no_recurring),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            pace?.takeIf { it.targetShare > 0 }?.let {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SectionTitle(stringResource(R.string.forecast_pace_title))
                Text(paceText(it), style = MaterialTheme.typography.bodyLarge)
                PaceBar(it)
                Text(
                    stringResource(
                        R.string.forecast_pace_detail,
                        formatCurrency(it.income, trimWholeDecimals = true),
                        formatCurrency(it.owed, trimWholeDecimals = true),
                        formatCurrency(it.given, trimWholeDecimals = true)
                    ),
                    style = MaterialTheme.typography.bodyMedium.tabular,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            taxCredit?.takeIf { it.eligible > 0 }?.let { tax ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SectionTitle(stringResource(R.string.tax_credit_title))
                val ils = SupportedCurrency.ILS.code
                Text(
                    if (tax.reachedMinimum) {
                        stringResource(
                            R.string.forecast_tax_projection,
                            formatCurrency(tax.eligible, trimWholeDecimals = true, currencyCode = ils),
                            formatCurrency(tax.credit, trimWholeDecimals = true, currencyCode = ils)
                        )
                    } else {
                        stringResource(
                            R.string.tax_credit_below_minimum,
                            formatCurrency(tax.minimum, trimWholeDecimals = true, currencyCode = ils)
                        )
                    },
                    style = MaterialTheme.typography.bodyLarge
                )
                Note(stringResource(R.string.tax_credit_estimate_note))
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
}

@Composable
private fun SourceRow(source: SourceProjection) {
    val counts = buildList {
        if (source.remaining > 0) {
            add(pluralStringResource(R.plurals.forecast_remaining, source.remaining, source.remaining))
        }
        if (source.skipped > 0) {
            add(pluralStringResource(R.plurals.forecast_skipped, source.skipped, source.skipped))
        }
    }.joinToString(" · ")
    LabelValueRow(
        label = source.name,
        value = formatSignedCurrency(abs(source.delta), isNegative = source.delta < 0),
        valueStyle = MaterialTheme.typography.bodyLarge.tabular,
        valueColor = if (source.isDonation) MaterialTheme.maserColors.donation else MaterialTheme.maserColors.income
    )
    Text(
        stringResource(R.string.forecast_source_detail, formatCurrency(source.amount, trimWholeDecimals = true), counts),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
