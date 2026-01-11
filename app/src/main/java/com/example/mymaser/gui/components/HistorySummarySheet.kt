package com.example.mymaser.gui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.mymaser.R
import com.example.mymaser.history.History
import com.example.mymaser.history.HistoryRepository
import com.example.mymaser.history.HistoryRepository.Companion.loadHistorySummary
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import kotlin.math.roundToInt

@Composable
fun HistorySummarySheet(
    history: History,
    onDismiss: () -> Unit
) {
    var offsetY by remember { mutableStateOf(0f) }
    val animatedOffsetY by animateFloatAsState(targetValue = offsetY)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 32.dp)
            .shadow(16.dp, RoundedCornerShape(24.dp))
            .alpha(1f - (offsetY / 600).coerceIn(0f, 1f))
            .offset { IntOffset(0, animatedOffsetY.roundToInt()) }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (offsetY > 250) {
                            onDismiss()
                        } else {
                            offsetY = 0f
                        }
                    },
                    onDragCancel = { offsetY = 0f },
                    onVerticalDrag = { _, dragAmount ->
                        val newOffset = offsetY + dragAmount
                        if (newOffset >= 0f) offsetY = newOffset
                    }
                )
            },
        shape = RoundedCornerShape(24.dp),
        elevation = 16.dp,
        color = androidx.compose.ui.graphics.Color.White
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            HistorySummaryContent(history)
        }
    }
}

@Composable
fun HistorySummaryContent(
    history: History
) {
    var summary by remember { mutableStateOf<HistoryRepository.HistorySummary?>(null) }

    LaunchedEffect(history.id) {
        val calendar = Calendar.getInstance()

        val startOfMonth = calendar.apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
        }.timeInMillis

        val endOfMonth = Calendar.getInstance().timeInMillis

        val startOfYear = Calendar.getInstance().apply {
            set(Calendar.MONTH, 0)
            set(Calendar.DAY_OF_MONTH, 1)
        }.timeInMillis

        val endOfYear = Calendar.getInstance().timeInMillis

        summary = loadHistorySummary(
            history,
            startOfMonth,
            endOfMonth,
            startOfYear,
            endOfYear
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
    ) {
        Text(history.name, style = MaterialTheme.typography.h5)

        Spacer(Modifier.height(16.dp))

        SummaryRow(stringResource(R.string.amount), history.amount.toString())
        SummaryRow(
            stringResource(R.string.type),
            stringResource(if (history.isDonation) R.string.donation else R.string.income)
        )
        SummaryRow(
            stringResource(R.string.date),
            DateFormat.getDateInstance().format(Date(history.timeStamp))
        )

        summary?.let {
            Spacer(Modifier.height(16.dp))
            SummaryRow(stringResource(R.string.total), it.totalByName.toString())
            SummaryRow(stringResource(R.string.entries_count), it.count.toString())
            SummaryRow(stringResource(R.string.this_month), it.monthTotal.toString())
            SummaryRow(stringResource(R.string.this_year), it.yearTotal.toString())
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.body1)
        Text(value, style = MaterialTheme.typography.body1)
    }
}