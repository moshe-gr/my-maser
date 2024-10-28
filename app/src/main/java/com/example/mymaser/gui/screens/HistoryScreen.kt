package com.example.mymaser.gui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.mymaser.R
import com.example.mymaser.history.History
import com.example.mymaser.history.HistoryRepository.Companion.getAllHistoryGroupedByMonth
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen() {
    val historyItems = remember { getAllHistoryGroupedByMonth() }

    if (historyItems.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center)
        {
            Text(
                text = stringResource(R.string.no_history_found),
                style = MaterialTheme.typography.body1
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            historyItems.forEach { (monthYear, historyItems) ->
                item {
                    MonthDivider(monthYear)
                }
                itemsIndexed(historyItems, key = { _, history -> history.id }) { _, history ->
                    HistoryItem(history)
                }
            }
        }
    }
}

@Composable
fun HistoryItem(history: History) {
    val dateFormatter = remember {
        DateFormat.getDateTimeInstance(
            DateFormat.MEDIUM,
            DateFormat.SHORT,
            Locale.getDefault()
        )
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(color = colorResource(id = if (history.isDonation) R.color.donation_red else R.color.income_green))
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = history.name,
                color = colorResource(id = R.color.colorPrimary),
                style = MaterialTheme.typography.h6
            )
            Spacer(modifier = Modifier.padding(vertical = 2.dp))
            Text(
                text = String.format("Amount: ₪%,.2f", history.amount),
                color = colorResource(id = R.color.colorPrimary),
                style = MaterialTheme.typography.body1
            )
            Spacer(modifier = Modifier.padding(vertical = 2.dp))
            Text(
                text = dateFormatter.format(Date(history.timeStamp)),
                color = colorResource(id = R.color.colorPrimary).copy(alpha = 0.8f),
                style = MaterialTheme.typography.caption
            )
        }
    }
}

@Composable
fun MonthDivider(monthYear: String) {
    Text(
        text = monthYear,
        color = colorResource(id = R.color.text),
        style = MaterialTheme.typography.h5,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        textAlign = TextAlign.Center
    )
}
