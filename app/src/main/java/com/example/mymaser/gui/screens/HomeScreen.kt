package com.example.mymaser.gui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mymaser.R
import com.example.mymaser.gui.components.EmptyState
import com.example.mymaser.gui.components.ScreenColumn
import com.example.mymaser.gui.components.surfaceCardColors
import com.example.mymaser.gui.LocalDisplayCurrency
import com.example.mymaser.gui.formatCurrency
import com.example.mymaser.gui.theme.MaserMotion
import com.example.mymaser.gui.theme.MaserPreview
import com.example.mymaser.gui.theme.ScreenPreviews
import com.example.mymaser.gui.theme.maserColors
import com.example.mymaser.gui.theme.tabular
import com.example.mymaser.history.History
import com.example.mymaser.history.HomeStats
import com.example.mymaser.history.computeHomeStats
import com.example.mymaser.settings.DEFAULT_MASER_PERCENTAGE
import com.example.mymaser.settings.formatPercentage
import java.text.DateFormat
import java.time.ZonedDateTime
import java.util.Date
import kotlin.math.abs

private const val ZERO_TOLERANCE = 0.005f

@Composable
fun HomeScreen(
    history: List<History>?,
    totalMaser: Float?,
    lastShownTotal: Float?,
    onTotalShown: (Float) -> Unit,
    onAddIncome: () -> Unit,
    onAddDonation: () -> Unit,
    maserPercentage: Float = DEFAULT_MASER_PERCENTAGE,
    notice: @Composable () -> Unit = {},
    dueCards: @Composable () -> Unit = {}
) {
    if (history == null || totalMaser == null) return
    val currency = LocalDisplayCurrency.current
    val stats = remember(history, currency) {
        computeHomeStats(history.filter { it.currency == currency }, ZonedDateTime.now())
    }
    HomeContent(
        total = totalMaser,
        countFrom = lastShownTotal ?: totalMaser,
        stats = stats,
        onTotalShown = onTotalShown,
        onAddIncome = onAddIncome,
        onAddDonation = onAddDonation,
        maserPercentage = maserPercentage,
        notice = notice,
        dueCards = dueCards
    )
}

@Composable
private fun HomeContent(
    total: Float,
    countFrom: Float,
    stats: HomeStats,
    onTotalShown: (Float) -> Unit,
    onAddIncome: () -> Unit,
    onAddDonation: () -> Unit,
    maserPercentage: Float = DEFAULT_MASER_PERCENTAGE,
    notice: @Composable () -> Unit = {},
    dueCards: @Composable () -> Unit = {}
) {
    ScreenColumn {
        TotalCard(total = total, countFrom = countFrom, onShown = onTotalShown)
        notice()
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickAction(
                label = stringResource(R.string.add_income),
                icon = Icons.Filled.AccountBalanceWallet,
                onClick = onAddIncome,
                modifier = Modifier.weight(1f)
            )
            QuickAction(
                label = stringResource(R.string.add_donation),
                icon = Icons.Filled.VolunteerActivism,
                onClick = onAddDonation,
                modifier = Modifier.weight(1f)
            )
        }
        dueCards()
        if (stats.lastActionTime == null) {
            EmptyState(
                icon = Icons.Filled.VolunteerActivism,
                message = stringResource(
                    R.string.home_empty_message,
                    formatPercentage(maserPercentage)
                ),
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            DonatedCard(stats)
            LastAction(stats.lastActionTime)
        }
    }
}

@Composable
private fun TotalCard(total: Float, countFrom: Float, onShown: (Float) -> Unit) {
    val counted = remember { Animatable(countFrom) }
    LaunchedEffect(total) {
        counted.animateTo(total, MaserMotion.totalCount)
        onShown(total)
    }

    val owes = total > ZERO_TOLERANCE
    val label = stringResource(
        when {
            owes -> R.string.maser_to_give
            total < -ZERO_TOLERANCE -> R.string.maser_given_ahead
            else -> R.string.maser_all_given
        }
    )
    val container by animateColorAsState(
        if (owes) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.maserColors.incomeContainer,
        label = "total_container"
    )
    val content = if (owes) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val finalAmount = formatCurrency(abs(total).toDouble(), trimWholeDecimals = true)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = "$label $finalAmount" },
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = label, style = MaterialTheme.typography.titleMedium)
            BasicText(
                text = formatCurrency(abs(counted.value).toDouble(), trimWholeDecimals = true),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.displayLarge.tabular.copy(
                    color = content,
                    textAlign = TextAlign.Center
                ),
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(
                    minFontSize = 28.sp,
                    maxFontSize = MaterialTheme.typography.displayLarge.fontSize,
                    stepSize = 2.sp
                )
            )
        }
    }
}

@Composable
private fun QuickAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.small
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

@Composable
private fun DonatedCard(stats: HomeStats) {
    Card(modifier = Modifier.fillMaxWidth(), colors = surfaceCardColors()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.donated_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            StatRow(stringResource(R.string.this_month), stats.donatedThisMonth)
            StatRow(stringResource(R.string.this_year), stats.donatedThisYear)
            StatRow(stringResource(R.string.total), stats.donatedTotal)
        }
    }
}

@Composable
private fun StatRow(label: String, amount: Double) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = formatCurrency(amount),
            style = MaterialTheme.typography.titleMedium.tabular,
            color = MaterialTheme.maserColors.donation
        )
    }
}

@Composable
private fun LastAction(time: Long) {
    val formatter = remember {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Outlined.Schedule,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.last_action_at, formatter.format(Date(time))),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@ScreenPreviews
@Composable
private fun HomePreview() {
    MaserPreview {
        HomeContent(
            total = 1234.5f,
            countFrom = 1234.5f,
            stats = HomeStats(180.0, 2160.0, 8640.5, 1_790_000_000_000L),
            onTotalShown = {},
            onAddIncome = {},
            onAddDonation = {}
        )
    }
}

@ScreenPreviews
@Composable
private fun HomeEmptyPreview() {
    MaserPreview {
        HomeContent(
            total = 0f,
            countFrom = 0f,
            stats = HomeStats(),
            onTotalShown = {},
            onAddIncome = {},
            onAddDonation = {}
        )
    }
}
