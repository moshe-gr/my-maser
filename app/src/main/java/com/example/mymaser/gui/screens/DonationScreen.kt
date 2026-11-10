package com.example.mymaser.gui.screens

import androidx.compose.runtime.Composable
import com.example.mymaser.gui.components.EntryForm
import com.example.mymaser.gui.theme.MaserPreview
import com.example.mymaser.gui.theme.ScreenPreviews
import com.example.mymaser.history.History

@Composable
fun DonationScreen(
    names: List<String>,
    lastEntry: History?,
    onSuggestionPicked: suspend (String) -> Float,
    onSave: (String, Float) -> Unit
) {
    EntryForm(
        isDonation = true,
        names = names,
        lastEntry = lastEntry,
        onSuggestionPicked = onSuggestionPicked,
        onSave = onSave
    )
}

@ScreenPreviews
@Composable
private fun DonationPreview() {
    MaserPreview {
        DonationScreen(
            names = emptyList(),
            lastEntry = null,
            onSuggestionPicked = { 0F },
            onSave = { _, _ -> }
        )
    }
}
