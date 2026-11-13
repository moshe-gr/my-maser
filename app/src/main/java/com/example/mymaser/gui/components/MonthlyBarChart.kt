package com.example.mymaser.gui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.mymaser.R
import com.example.mymaser.gui.formatCurrency
import com.example.mymaser.gui.theme.tabular
import com.example.mymaser.history.MonthPair
import com.example.mymaser.history.MonthTotal
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val DIMMED_ALPHA = 0.45f
private val BarShape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)

private fun YearMonth.shortLabel() =
    format(DateTimeFormatter.ofPattern("LLL", Locale.getDefault()))

/**
 * One bar per month in a single hue. Tapping a column selects it and shows its exact total
 * above the chart; each column is also announced as "month: amount" for screen readers.
 * Months at or below zero get no bar.
 */
@Composable
fun MonthlyBarChart(
    months: List<MonthTotal>,
    color: Color,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.summary_last_12_months),
    initialSelection: Int = months.lastIndex
) {
    if (months.isEmpty()) return
    val max = months.maxOf { it.total }.takeIf { it > 0 } ?: 1.0
    BarChartFrame(
        title = title,
        months = months.map { it.month },
        initialSelection = initialSelection,
        valueTexts = months.map { formatCurrency(it.total) },
        modifier = modifier
    ) { index, selected ->
        Bar(months[index].total / max, color, selected)
    }
}

/** Income and donations side by side for each month, with a legend. */
@Composable
fun DualBarChart(
    months: List<MonthPair>,
    title: String,
    incomeColor: Color,
    donationColor: Color,
    modifier: Modifier = Modifier
) {
    if (months.isEmpty()) return
    val max = months.maxOf { maxOf(it.income, it.donations) }.takeIf { it > 0 } ?: 1.0
    val incomeLabel = stringResource(R.string.income)
    val donationLabel = stringResource(R.string.donations)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BarChartFrame(
            title = title,
            months = months.map { it.month },
            initialSelection = months.lastIndex,
            valueTexts = months.map {
                "$incomeLabel ${formatCurrency(it.income)} · $donationLabel ${formatCurrency(it.donations)}"
            }
        ) { index, selected ->
            Bar(months[index].income / max, incomeColor, selected)
            Bar(months[index].donations / max, donationColor, selected)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendItem(incomeLabel, incomeColor)
            LegendItem(donationLabel, donationColor)
        }
    }
}

@Composable
private fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RowScope.Bar(fraction: Double, color: Color, selected: Boolean) {
    val animated by animateFloatAsState(fraction.toFloat().coerceIn(0f, 1f), label = "bar_height")
    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
        if (fraction > 0) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(animated.coerceAtLeast(0.02f))
                    .background(if (selected) color else color.copy(alpha = DIMMED_ALPHA), BarShape)
            )
        }
    }
}

@Composable
private fun BarChartFrame(
    title: String,
    months: List<YearMonth>,
    initialSelection: Int,
    valueTexts: List<String>,
    modifier: Modifier = Modifier,
    bars: @Composable RowScope.(index: Int, selected: Boolean) -> Unit
) {
    var selected by rememberSaveable(months.size, initialSelection) { mutableIntStateOf(initialSelection) }
    val current = selected.coerceIn(months.indices)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            text = "${months[current].monthYearLabel()} · ${valueTexts[current]}",
            style = MaterialTheme.typography.bodyMedium.tabular,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            months.forEachIndexed { index, month ->
                val description = stringResource(
                    R.string.summary_bar_description,
                    month.monthYearLabel(),
                    valueTexts[index]
                )
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectable(
                            selected = index == current,
                            role = Role.Tab,
                            onClick = { selected = index }
                        )
                        .semantics { contentDescription = description }
                        .padding(horizontal = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(1.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    bars(index, index == current)
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth()) {
            val labelled = setOf(0, months.size / 2, months.lastIndex)
            months.forEachIndexed { index, month ->
                Text(
                    text = if (index in labelled) month.shortLabel() else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
