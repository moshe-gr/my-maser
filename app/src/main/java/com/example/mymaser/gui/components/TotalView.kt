package com.example.mymaser.gui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.mymaser.R
import java.util.Locale

@Composable
fun TotalMaserView(totalMaser: Float) {
    Card(
        elevation = 16.dp
    ) {
        Text(
            text = String.format(Locale.getDefault(), "%,.0f", totalMaser),
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 8.dp),
            color = colorResource(id = R.color.light_blue),
            style = MaterialTheme.typography.h1,
            fontWeight = FontWeight.SemiBold
        )
    }
}