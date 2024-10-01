package com.example.mymaser.gui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.mymaser.history.History
import com.example.mymaser.history.HistoryRepository.Companion.getAllHistoryGroupedByMonth
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen() {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        getAllHistoryGroupedByMonth().forEach { (monthYear, historyItems) ->
            item {
                MonthDivider(monthYear)
            }
            itemsIndexed(historyItems, key = { _, history -> history.id }) { _, history ->
                HistoryItem(history)
            }
        }
    }
}

@Composable
fun HistoryItem(history: History) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(color = colorResource(id = if (history.isDonation) android.R.color.holo_red_light else android.R.color.holo_green_light))
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = history.name, style = MaterialTheme.typography.h6)
            Text(text = "Amount: ₪${history.amount}", style = MaterialTheme.typography.body1)
            Text(
                text = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(
                    Date(history.timeStamp)
                ), style = MaterialTheme.typography.caption
            )
        }
    }
}

@Composable
fun MonthDivider(monthYear: String) {
    Text(
        text = monthYear,
        style = MaterialTheme.typography.h5,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        textAlign = TextAlign.Center
    )
}
