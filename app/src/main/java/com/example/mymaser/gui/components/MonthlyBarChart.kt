package com.example.mymaser.gui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
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
import com.example.mymaser.history.MonthTotal
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val DIMMED_ALPHA = 0.45f
private val BarShape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)

private fun YearMonth.longLabel() =
    format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.getDefault()))

private fun YearMonth.shortLabel() =
    format(DateTimeFormatter.ofPattern("LLL", Locale.getDefault()))

/**
 * One bar per month in a single hue. Tapping a column selects it and shows its exact total
 * above the chart; each column is also announced as "month: amount" for screen readers.
 */
@Composable
fun MonthlyBarChart(
    months: List<MonthTotal>,
    color: Color,
    modifier: Modifier = Modifier
) {
    if (months.isEmpty()) return
    var selected by rememberSaveable(months.size) { mutableIntStateOf(months.lastIndex) }
    val max = months.maxOf { it.total }.takeIf { it > 0 } ?: 1.0
    val selectedMonth = months[selected.coerceIn(months.indices)]

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.summary_last_12_months),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            text = "${selectedMonth.month.longLabel()} · ${formatCurrency(selectedMonth.total)}",
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
                val fraction by animateFloatAsState(
                    (month.total / max).toFloat(),
                    label = "bar_height"
                )
                val description = stringResource(
                    R.string.summary_bar_description,
                    month.month.longLabel(),
                    formatCurrency(month.total)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectable(
                            selected = index == selected,
                            role = Role.Tab,
                            onClick = { selected = index }
                        )
                        .semantics { contentDescription = description }
                        .padding(horizontal = 3.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    if (month.total > 0) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(fraction.coerceAtLeast(0.02f))
                                .background(
                                    color = if (index == selected) color else color.copy(alpha = DIMMED_ALPHA),
                                    shape = BarShape
                                )
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth()) {
            val labelled = setOf(0, months.size / 2, months.lastIndex)
            months.forEachIndexed { index, month ->
                Text(
                    text = if (index in labelled) month.month.shortLabel() else "",
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
