package com.example.mymaser.gui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mymaser.R
import com.example.mymaser.history.History
import java.text.DateFormat
import java.util.Date

@Preview
@Composable
fun HistoryItem(history: History = History()) {
    Row(
        modifier = Modifier
            .wrapContentHeight()
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        SingleTextBox(
            text = stringResource(id = if (history.isDonation) R.string.donation else R.string.income),
            modifier = Modifier.weight(1f)
        )
        SingleTextBox(text = history.name, modifier = Modifier.weight(1f))
        SingleTextBox(text = history.amount.toString(), modifier = Modifier.weight(1f))
        SingleTextBox(
            text = DateFormat.getDateInstance(DateFormat.SHORT).format(Date(history.timeStamp)),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SingleTextBox(text: String, modifier: Modifier) {
    Text(
        text = text,
        modifier = modifier
            .border(width = 0.8.dp, color = colorResource(id = R.color.black))
            .padding(4.dp),
        fontSize = 17.sp,
        textAlign = TextAlign.Center,
        overflow = TextOverflow.Ellipsis,
        maxLines = 1
    )
}