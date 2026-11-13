package com.example.mymaser.gui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.mymaser.R
import com.example.mymaser.gui.formatCurrency
import com.example.mymaser.gui.formatPercent
import com.example.mymaser.history.YearEndForecast
import com.example.mymaser.history.YearPace

private const val CLEAR_TOLERANCE = 0.5
private const val PACE_DIGITS = 1

@Composable
fun forecastText(forecast: YearEndForecast): String =
    if (forecast.balance > CLEAR_TOLERANCE) {
        stringResource(
            R.string.forecast_owed,
            formatCurrency(forecast.balance, trimWholeDecimals = true),
            formatCurrency(forecast.monthlyToClear, trimWholeDecimals = true)
        )
    } else {
        stringResource(R.string.forecast_clear)
    }

@Composable
fun paceText(pace: YearPace): String = stringResource(
    R.string.forecast_pace,
    formatPercent(pace.givenShare, maxFractionDigits = PACE_DIGITS),
    formatPercent(pace.targetShare, maxFractionDigits = PACE_DIGITS)
)

/** Given against owed this year, full at the target. */
@Composable
fun PaceBar(pace: YearPace, modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        progress = { (pace.givenShare / pace.targetShare).toFloat().coerceIn(0f, 1f) },
        modifier = modifier.fillMaxWidth()
    )
}

/** Where the maser balance is heading by December 31 and this year's pace; opens the breakdown. */
@Composable
fun ForecastCard(forecast: YearEndForecast?, pace: YearPace?, onClick: () -> Unit) {
    val shownPace = pace?.takeIf { it.targetShare > 0 }
    if (forecast == null && shownPace == null) return
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.TrendingUp,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                forecast?.let {
                    Text(
                        text = forecastText(it),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                shownPace?.let {
                    Text(
                        text = paceText(it),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    PaceBar(it)
                }
            }
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = stringResource(R.string.forecast_details),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
