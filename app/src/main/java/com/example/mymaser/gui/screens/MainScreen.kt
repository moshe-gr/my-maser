package com.example.mymaser.gui.screens

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.ContentAlpha
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.Scaffold
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.mymaser.R
import com.example.mymaser.gui.ScreenTypes
import com.example.mymaser.settings.SettingsActivity

@Composable
fun MainScreen(totalMaser: Float, onEdit: (Float) -> Unit) {
    val navController = rememberNavController()
    val context = LocalContext.current

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(colorResource(id = R.color.colorPrimary))
            .systemBarsPadding(),
        topBar = {
            TopNavigationBar(navController = navController)
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    context.startActivity(Intent(context, SettingsActivity::class.java))
                },
                backgroundColor = colorResource(id = R.color.colorPrimary),
                contentColor = colorResource(id = R.color.colorOnPrimary)
            ) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = stringResource(R.string.settings)
                )
            }
        },
        backgroundColor = colorResource(id = R.color.bg_color)
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            NavigationHost(
                navController = navController,
                totalMaser = totalMaser,
                onEdit = onEdit
            )
        }
    }
}

@Composable
fun TopNavigationBar(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val selectedTabIndex = ScreenTypes.entries.indexOfFirst { screen ->
        currentDestination?.hierarchy?.any { destination -> destination.route == screen.route } == true
    }.takeIf { it >= 0 } ?: 0

    TabRow(
        selectedTabIndex = selectedTabIndex,
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
                        alpha = if (selectedTabIndex == index) 1f else ContentAlpha.medium,
                        colorFilter = ColorFilter.tint(
                            colorResource(id = R.color.colorOnPrimary)
                        )
                    )
                },
                selected = selectedTabIndex == index,
                onClick = {
                    navController.navigate(screenType.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    }
}

@Composable
fun NavigationHost(
    navController: NavHostController,
    totalMaser: Float,
    onEdit: (Float) -> Unit
) {
    NavHost(
        navController = navController,
        startDestination = ScreenTypes.Home.route
    ) {
        composable(ScreenTypes.Home.route) {
            HomeScreen(totalMaser)
        }
        composable(ScreenTypes.Income.route) {
            IncomeScreen(onEdit)
        }
        composable(ScreenTypes.Donation.route) {
            DonationScreen(onEdit)
        }
        composable(ScreenTypes.History.route) {
            HistoryScreen()
        }
    }
}
