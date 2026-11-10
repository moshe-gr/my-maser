package com.example.mymaser.gui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.mymaser.R

enum class ScreenTypes(
    val route: String,
    @StringRes val label: Int,
    val selectedIcon: ImageVector,
    val icon: ImageVector
) {
    Home("home", R.string.home, Icons.Filled.Home, Icons.Outlined.Home),
    Income(
        "income",
        R.string.income,
        Icons.Filled.AccountBalanceWallet,
        Icons.Outlined.AccountBalanceWallet
    ),
    Donation(
        "donation",
        R.string.donate,
        Icons.Filled.VolunteerActivism,
        Icons.Outlined.VolunteerActivism
    ),
    History("history", R.string.history, Icons.Filled.History, Icons.Outlined.History)
}
