@file:Suppress("PreviewAnnotationInFunctionWithParameters")

package com.example.mymaser.gui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.mymaser.R
import com.example.mymaser.gui.components.TotalMaserView
import com.example.mymaser.history.HistoryRepository.Companion.getHistoryForLastMonth
import com.example.mymaser.history.HistoryRepository.Companion.getHistoryForLastYear
import com.example.mymaser.history.HistoryRepository.Companion.getLastActionTime
import com.example.mymaser.history.HistoryRepository.Companion.getTotalMaser

@Preview
@Composable
fun HomeScreen(totalMaser: Float) {
    Column {
        Spacer(modifier = Modifier.fillMaxHeight(0.33f))
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TotalMaserView(totalMaser)
            Card {
                Column(
                    modifier = Modifier
                        .width(IntrinsicSize.Max)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.this_month),
                            color = colorResource(id = R.color.text),
                            style = MaterialTheme.typography.h5,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${getHistoryForLastMonth(true)}",
                            modifier = Modifier.weight(1f),
                            color = colorResource(id = R.color.secondary_text),
                            style = MaterialTheme.typography.h6,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.End
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.this_year),
                            color = colorResource(id = R.color.text),
                            style = MaterialTheme.typography.h5,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${getHistoryForLastYear(true)}",
                            modifier = Modifier.weight(1f),
                            color = colorResource(id = R.color.secondary_text),
                            style = MaterialTheme.typography.h6,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.End
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.total),
                            color = colorResource(id = R.color.text),
                            style = MaterialTheme.typography.h5,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${getTotalMaser()}",
                            modifier = Modifier.weight(1f),
                            color = colorResource(id = R.color.secondary_text),
                            style = MaterialTheme.typography.h6,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.End
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = colorResource(id = R.color.colorPrimary),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.size(12.dp))
                Text(
                    text = stringResource(R.string.last_action_at, getLastActionTime()),
                    color = colorResource(id = R.color.text),
                    style = MaterialTheme.typography.caption
                )
            }
        }
    }
}