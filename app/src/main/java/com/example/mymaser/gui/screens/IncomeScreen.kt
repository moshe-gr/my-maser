package com.example.mymaser.gui.screens

import androidx.compose.runtime.Composable
import com.example.mymaser.gui.components.EntryForm
import com.example.mymaser.gui.theme.MaserPreview
import com.example.mymaser.gui.theme.ScreenPreviews
import com.example.mymaser.history.AmountSuggestion
import com.example.mymaser.history.History
import com.example.mymaser.history.Prefill

@Composable
fun IncomeScreen(
    names: List<String>,
    lastEntry: History?,
    onSuggestionPicked: (String) -> AmountSuggestion?,
    onSave: (name: String, amount: Float, currency: String) -> Unit,
    maserPercentage: Float,
    prefill: Prefill? = null,
    onPrefillConsumed: () -> Unit = {}
) {
    EntryForm(
        isDonation = false,
        names = names,
        lastEntry = lastEntry,
        onSuggestionPicked = onSuggestionPicked,
        onSave = onSave,
        maserPercentage = maserPercentage,
        prefill = prefill,
        onPrefillConsumed = onPrefillConsumed
    )
}

@ScreenPreviews
@Composable
private fun IncomePreview() {
    MaserPreview {
        IncomeScreen(
            names = emptyList(),
            lastEntry = History(name = "Salary", amount = 12000F),
            onSuggestionPicked = { null },
            onSave = { _, _, _ -> },
            maserPercentage = 10f
        )
    }
}
