package com.example.mymaser.gui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.example.mymaser.R

enum class ScreenTypes(@StringRes var label: Int = -1, @DrawableRes var icon: Int = -1) {
    Home(label = R.string.home, icon = R.drawable.twotone_home_24),
    Income(label = R.string.income, icon = R.drawable.twotone_attach_money_24),
    Donation(label = R.string.donate, icon = R.drawable.twotone_currency_exchange_24),
    History(label = R.string.history, icon = R.drawable.twotone_history_24)

}