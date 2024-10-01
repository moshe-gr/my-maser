@file:Suppress("PreviewAnnotationInFunctionWithParameters")

package com.example.mymaser.gui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Card
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mymaser.R
import com.example.mymaser.history.HistoryRepository.Companion.getHistoryForLastMonth
import com.example.mymaser.history.HistoryRepository.Companion.getHistoryForLastYear
import com.example.mymaser.history.HistoryRepository.Companion.getLastActionTime
import com.example.mymaser.history.HistoryRepository.Companion.getTotalMaser
import kotlin.text.Typography.nbsp

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
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.this_month),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$nbsp${getHistoryForLastMonth(true)}",
                            fontSize = 21.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Row(
                        modifier = Modifier.padding(vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.this_year),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$nbsp${getHistoryForLastYear(true)}",
                            fontSize = 21.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.total),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$nbsp${getTotalMaser()}",
                            fontSize = 21.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            Text(text = stringResource(R.string.last_action_at, getLastActionTime()))
        }
    }
}

@Composable
fun TotalMaserView(totalMaser: Float) {
    Card(
        elevation = 16.dp
    ) {
        Text(
            text = totalMaser.toString(),
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 8.dp),
            color = colorResource(id = android.R.color.holo_blue_bright),
            fontSize = 64.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}