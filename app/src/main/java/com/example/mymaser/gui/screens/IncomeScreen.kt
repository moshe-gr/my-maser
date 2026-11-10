package com.example.mymaser.gui.screens

import androidx.compose.runtime.Composable
import com.example.mymaser.gui.components.EntryForm
import com.example.mymaser.gui.theme.MaserPreview
import com.example.mymaser.gui.theme.ScreenPreviews
import com.example.mymaser.history.History

@Composable
fun IncomeScreen(
    names: List<String>,
    lastEntry: History?,
    onSuggestionPicked: suspend (String) -> Float,
    onSave: (String, Float) -> Unit,
    maserPercentage: Float
) {
    EntryForm(
        isDonation = false,
        names = names,
        lastEntry = lastEntry,
        onSuggestionPicked = onSuggestionPicked,
        onSave = onSave,
        maserPercentage = maserPercentage
    )
}

@ScreenPreviews
@Composable
private fun IncomePreview() {
    MaserPreview {
        IncomeScreen(
            names = emptyList(),
            lastEntry = History(name = "Salary", amount = 12000F),
            onSuggestionPicked = { 0F },
            onSave = { _, _ -> },
            maserPercentage = 10f
        )
    }
}
