package com.example.mymaser.gui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import com.example.mymaser.R

@Composable
fun SuggestionsList(suggestions: List<String>, onSuggestionSelected: (String) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxWidth(0.5f)) {
        itemsIndexed(suggestions) { index, suggestion ->
            Text(
                text = suggestion,
                modifier = Modifier
                    .padding(8.dp)
                    .clickable {
                        onSuggestionSelected(suggestion)
                    },
                color = colorResource(id = R.color.text)
            )
            if (index < suggestions.lastIndex) {
                Divider(
                    color = colorResource(id = R.color.secondary_text).copy(alpha = 0.5f),
                    thickness = 0.5.dp
                )
            }
        }
    }
}
