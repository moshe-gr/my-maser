package com.example.mymaser.gui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Scaffold
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.example.mymaser.R
import com.example.mymaser.gui.ScreenTypes

@Composable
fun MainScreen(totalMaser: Float, onEdit: (Float) -> Unit) {
    var tabIndex by rememberSaveable { mutableIntStateOf(ScreenTypes.Home.ordinal) }
    Scaffold(
        modifier = Modifier
            .fillMaxSize(),
        topBar = {
            TabRow(
                selectedTabIndex = tabIndex,
                backgroundColor = colorResource(id = R.color.colorPrimary),
                contentColor = colorResource(id = R.color.colorOnPrimary)
            ) {
                ScreenTypes.entries.forEachIndexed { index, screenType ->
                    Tab(
                        text = {
                            Text(
                                text = stringResource(id = screenType.label)
                            )
                        },
                        icon = {
                            Image(
                                painter = painterResource(id = screenType.icon),
                                contentDescription = null,
                                alpha = if (tabIndex == index) 1f else ContentAlpha.medium,
                                colorFilter = ColorFilter.tint(
                                    colorResource(
                                        id = R.color.colorOnPrimary
                                    )
                                )
                            )
                        },
                        selected = tabIndex == index,
                        onClick = { tabIndex = index }
                    )
                }
            }
        },
        backgroundColor = colorResource(id = R.color.bg_color)
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (tabIndex) {
                ScreenTypes.Home.ordinal -> HomeScreen(totalMaser)
                ScreenTypes.Income.ordinal -> IncomeScreen(onEdit)
                ScreenTypes.Donation.ordinal -> DonationScreen(onEdit)
                ScreenTypes.History.ordinal -> HistoryScreen()
            }
        }
    }
}