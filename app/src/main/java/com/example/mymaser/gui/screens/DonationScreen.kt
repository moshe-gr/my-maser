package com.example.mymaser.gui.screens

import androidx.compose.runtime.Composable
import com.example.mymaser.gui.components.EntryForm
import com.example.mymaser.gui.theme.MaserPreview
import com.example.mymaser.gui.theme.ScreenPreviews
import com.example.mymaser.history.AmountSuggestion
import com.example.mymaser.history.EntryWarning
import com.example.mymaser.history.History
import com.example.mymaser.history.Prefill

@Composable
fun DonationScreen(
    names: List<String>,
    lastEntry: History?,
    onSuggestionPicked: (String) -> AmountSuggestion?,
    onSave: (name: String, amount: Float, currency: String) -> Unit,
    prefill: Prefill? = null,
    onPrefillConsumed: () -> Unit = {},
    onCheck: (String, Float, String) -> EntryWarning? = { _, _, _ -> null }
) {
    EntryForm(
        isDonation = true,
        names = names,
        lastEntry = lastEntry,
        onSuggestionPicked = onSuggestionPicked,
        onSave = onSave,
        prefill = prefill,
        onPrefillConsumed = onPrefillConsumed,
        onCheck = onCheck
    )
}

@ScreenPreviews
@Composable
private fun DonationPreview() {
    MaserPreview {
        DonationScreen(
            names = emptyList(),
            lastEntry = null,
            onSuggestionPicked = { null },
            onSave = { _, _, _ -> }
        )
    }
}
