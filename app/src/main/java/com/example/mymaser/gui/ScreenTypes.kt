package com.example.mymaser.gui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.mymaser.R

/** The top-level destinations shown in the navigation bar. */
enum class ScreenTypes(
    val route: String,
    @StringRes val label: Int,
    val selectedIcon: ImageVector,
    val icon: ImageVector
) {
    Home("home", R.string.home, Icons.Filled.Home, Icons.Outlined.Home),
    History("history", R.string.history, Icons.Filled.History, Icons.Outlined.History),
    Ask("chat", R.string.chat_tab, Icons.AutoMirrored.Filled.Chat, Icons.AutoMirrored.Outlined.Chat)
}
