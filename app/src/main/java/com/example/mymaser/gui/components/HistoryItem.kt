package com.example.mymaser.gui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.mymaser.R
import com.example.mymaser.gui.formatCurrency
import com.example.mymaser.gui.formatSignedCurrency
import com.example.mymaser.gui.theme.maserColors
import com.example.mymaser.gui.theme.tabular
import com.example.mymaser.history.History
import com.example.mymaser.history.isTaxOnlyDonation
import java.util.Date

@Composable
fun HistoryItem(
    history: History,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    original: History? = null
) {
    val dateFormatter = rememberDateTimeFormat()
    val accent = if (history.isDonation) MaterialTheme.maserColors.donation else MaterialTheme.maserColors.income

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = surfaceCardColors()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            EntryTypeBadge(history.isDonation)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = history.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = dateFormatter.format(Date(history.timeStamp)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (history.isTaxOnlyDonation) {
                    TaxOnlyTag(Modifier.padding(top = 4.dp))
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatSignedCurrency(
                        history.amount.toDouble(),
                        history.isDonation,
                        history.currency
                    ),
                    style = MaterialTheme.typography.titleMedium.tabular,
                    color = accent
                )
                if (original != null && original.currency != history.currency) {
                    Text(
                        text = stringResource(
                            R.string.originally_amount,
                            formatCurrency(original.amount.toDouble(), currencyCode = original.currency)
                        ),
                        style = MaterialTheme.typography.bodySmall.tabular,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
