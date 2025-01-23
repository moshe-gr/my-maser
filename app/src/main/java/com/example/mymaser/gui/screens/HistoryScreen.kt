package com.example.mymaser.gui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.mymaser.R
import com.example.mymaser.gui.components.HistoryItem
import com.example.mymaser.history.HistoryRepository.Companion.groupHistoryByTimePeriod

@Composable
fun HistoryScreen() {
    val context = LocalContext.current
    val historyItems = remember { groupHistoryByTimePeriod(context) }

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
