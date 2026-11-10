package com.example.mymaser.gui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.mymaser.R
import com.example.mymaser.gui.formatCurrency
import com.example.mymaser.gui.theme.maserColors
import com.example.mymaser.history.DueStatus
import com.example.mymaser.history.Prediction
import java.time.LocalDate
import kotlin.math.abs

/** Recurring entries expected around today, each one tap away from being logged. */
@Composable
fun DueCards(
    predictions: List<Prediction>,
    onLog: (Prediction) -> Unit,
    onOpen: (Prediction) -> Unit,
    onSkip: (Prediction) -> Unit
) {
    if (predictions.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.due_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() }
        )
        predictions.forEach { prediction ->
            DueCard(prediction, onLog, onOpen, onSkip)
        }
    }
}

@Composable
private fun DueCard(
    prediction: Prediction,
    onLog: (Prediction) -> Unit,
    onOpen: (Prediction) -> Unit,
    onSkip: (Prediction) -> Unit
) {
    val colors = MaterialTheme.maserColors
    val amount = formatCurrency(prediction.amount.toDouble(), currencyCode = prediction.currency)
    var menuOpen by remember { mutableStateOf(false) }

    Card(
        onClick = { onOpen(prediction) },
        modifier = Modifier.fillMaxWidth(),
        colors = surfaceCardColors()
    ) {
        Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            if (prediction.isDonation) colors.donationContainer else colors.incomeContainer,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (prediction.isDonation) {
                            Icons.Filled.VolunteerActivism
                        } else {
                            Icons.Filled.AccountBalanceWallet
                        },
                        contentDescription = stringResource(
                            if (prediction.isDonation) R.string.donation else R.string.income
                        ),
                        tint = if (prediction.isDonation) colors.donation else colors.income,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = prediction.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = dueLabel(prediction),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (prediction.status == DueStatus.Overdue) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, stringResource(R.string.due_more))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.due_skip)) },
                            onClick = {
                                menuOpen = false
                                onSkip(prediction)
                            }
                        )
                    }
                }
            }
            FilledTonalButton(
                onClick = { onLog(prediction) },
                modifier = Modifier.padding(start = 52.dp, top = 8.dp),
                shape = MaterialTheme.shapes.small
            ) {
                Text(stringResource(R.string.due_log, amount))
            }
        }
    }
}

@Composable
private fun dueLabel(prediction: Prediction): String {
    val days = prediction.daysUntil(LocalDate.now())
    return when {
        days == 0L -> stringResource(R.string.due_today)
        days > 0 -> pluralStringResource(R.plurals.due_in_days, days.toInt(), days.toInt())
        else -> {
            val late = abs(days).toInt()
            pluralStringResource(R.plurals.due_days_ago, late, late)
        }
    }
}
