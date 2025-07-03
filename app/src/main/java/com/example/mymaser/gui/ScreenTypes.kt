package com.example.mymaser.gui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.example.mymaser.R

enum class ScreenTypes(
    val route: String,
    @StringRes var label: Int = -1,
    @DrawableRes var icon: Int = -1
) {
    Home(route = "home", label = R.string.home, icon = R.drawable.twotone_home_24),
    Income(route = "income", label = R.string.income, icon = R.drawable.twotone_attach_money_24),
    Donation(
        route = "donation",
        label = R.string.donate,
        icon = R.drawable.twotone_currency_exchange_24
    ),
    History(route = "history", label = R.string.history, icon = R.drawable.twotone_history_24)
}