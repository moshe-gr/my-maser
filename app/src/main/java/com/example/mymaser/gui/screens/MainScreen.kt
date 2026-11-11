package com.example.mymaser.gui.screens

import android.content.Intent
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.mymaser.R
import com.example.mymaser.gui.ScreenTypes
import com.example.mymaser.gui.components.DueCards
import com.example.mymaser.gui.components.ForecastLine
import com.example.mymaser.gui.components.RatesNotice
import com.example.mymaser.gui.components.ReminderPromptCard
import com.example.mymaser.reminders.AppDestination
import com.example.mymaser.history.toPrefill
import androidx.compose.runtime.LaunchedEffect
import com.example.mymaser.gui.theme.MaserMotion
import com.example.mymaser.history.History
import com.example.mymaser.history.HistoryViewModel
import com.example.mymaser.settings.SettingsActivity
import kotlinx.coroutines.launch

private fun NavDestination?.isOn(screen: ScreenTypes) =
    this?.hierarchy?.any { it.route == screen.route } == true

private fun NavHostController.openTab(screen: ScreenTypes) {
    navigate(screen.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: HistoryViewModel = viewModel()) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val haptics = LocalHapticFeedback.current
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val current = ScreenTypes.entries.firstOrNull { destination.isOn(it) } ?: ScreenTypes.Home

    val history by viewModel.history.collectAsStateWithLifecycle()
    val totalMaser by viewModel.totalMaser.collectAsStateWithLifecycle()
    val incomeNames by viewModel.incomeNames.collectAsStateWithLifecycle()
    val donationNames by viewModel.donationNames.collectAsStateWithLifecycle()
    val maserPercentage by viewModel.maserPercentage.collectAsStateWithLifecycle()
    val unconvertedCount by viewModel.unconvertedCount.collectAsStateWithLifecycle()
    val rateStatus by viewModel.rateStatus.collectAsStateWithLifecycle()
    val predictions by viewModel.predictions.collectAsStateWithLifecycle()
    val prefill by viewModel.prefill.collectAsStateWithLifecycle()
    val requestedDestination by viewModel.destinationRequest.collectAsStateWithLifecycle()
    val missingReceipts by viewModel.missingReceiptsRequest.collectAsStateWithLifecycle()
    val reminderPrompt by viewModel.reminderPrompt.collectAsStateWithLifecycle()
    val forecast by viewModel.forecast.collectAsStateWithLifecycle()

    val undoLabel = stringResource(R.string.undo)
    val savedMessage = stringResource(R.string.information_saved_successfully)
    val deletedMessage = stringResource(R.string.entry_deleted)

    fun offerUndo(message: String, undo: () -> Unit) {
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = undoLabel,
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) undo()
        }
    }

    fun saveEntry(name: String, amount: Float, currency: String, isDonation: Boolean) {
        viewModel.addEntry(name, amount, currency, isDonation) { saved ->
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            offerUndo(savedMessage) { viewModel.deleteEntry(saved) }
        }
    }

    fun deleteEntry(entry: History) {
        val removed = viewModel.deleteEntry(entry)
        offerUndo(deletedMessage) { viewModel.restoreEntry(removed) }
    }

    LaunchedEffect(prefill) {
        prefill?.let { navController.openTab(if (it.isDonation) ScreenTypes.Donation else ScreenTypes.Income) }
    }

    LaunchedEffect(requestedDestination) {
        val target = when (requestedDestination) {
            AppDestination.Home -> ScreenTypes.Home
            AppDestination.Donation -> ScreenTypes.Donation
            AppDestination.History -> ScreenTypes.History
            null -> return@LaunchedEffect
        }
        navController.openTab(target)
        viewModel.consumeDestination()
    }

    val dueCards: @Composable () -> Unit = {
        DueCards(
            predictions = predictions,
            onLog = { saveEntry(it.name, it.amount, it.currency, it.isDonation) },
            onOpen = { viewModel.requestPrefill(it.toPrefill()) },
            onSkip = viewModel::dismissPrediction
        )
        reminderPrompt?.let { source ->
            ReminderPromptCard(
                sourceName = source,
                onEnable = viewModel::enableReminders,
                onDismiss = viewModel::dismissReminderPrompt
            )
        }
    }

    val ratesNotice: @Composable () -> Unit = {
        RatesNotice(
            unconverted = unconvertedCount,
            status = rateStatus,
            onRetry = viewModel::retryRates
        )
    }

    val homeNotice: @Composable () -> Unit = {
        forecast?.let { ForecastLine(it) }
        ratesNotice()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (current == ScreenTypes.Home) R.string.app_name else current.label
                        )
                    )
                },
                actions = {
                    IconButton(
                        onClick = {
                            context.startActivity(Intent(context, SettingsActivity::class.java))
                        }
                    ) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.settings)
                        )
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                ScreenTypes.entries.forEach { screen ->
                    val selected = current == screen
                    NavigationBarItem(
                        selected = selected,
                        onClick = { navController.openTab(screen) },
                        icon = {
                            Icon(
                                imageVector = if (selected) screen.selectedIcon else screen.icon,
                                contentDescription = null
                            )
                        },
                        label = { Text(stringResource(screen.label)) }
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = ScreenTypes.Home.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding(),
            enterTransition = { MaserMotion.tabEnter },
            exitTransition = { MaserMotion.tabExit },
            popEnterTransition = { MaserMotion.tabEnter },
            popExitTransition = { MaserMotion.tabExit }
        ) {
            composable(ScreenTypes.Home.route) {
                HomeScreen(
                    history = history,
                    totalMaser = totalMaser,
                    lastShownTotal = viewModel.lastShownTotal,
                    onTotalShown = { viewModel.lastShownTotal = it },
                    onAddIncome = { navController.openTab(ScreenTypes.Income) },
                    onAddDonation = { navController.openTab(ScreenTypes.Donation) },
                    maserPercentage = maserPercentage,
                    notice = homeNotice,
                    dueCards = dueCards
                )
            }
            composable(ScreenTypes.Income.route) {
                IncomeScreen(
                    names = incomeNames,
                    lastEntry = history?.firstOrNull { !it.isDonation },
                    onSuggestionPicked = { viewModel.suggestedAmount(it, isDonation = false) },
                    onSave = { name, amount, currency ->
                        saveEntry(name, amount, currency, isDonation = false)
                    },
                    maserPercentage = maserPercentage,
                    prefill = prefill,
                    onPrefillConsumed = viewModel::consumePrefill,
                    onCheck = { name, amount, currency ->
                        viewModel.checkNewEntry(name, amount, currency, isDonation = false)
                    }
                )
            }
            composable(ScreenTypes.Donation.route) {
                DonationScreen(
                    names = donationNames,
                    lastEntry = history?.firstOrNull { it.isDonation },
                    onSuggestionPicked = { viewModel.suggestedAmount(it, isDonation = true) },
                    onSave = { name, amount, currency ->
                        saveEntry(name, amount, currency, isDonation = true)
                    },
                    prefill = prefill,
                    onPrefillConsumed = viewModel::consumePrefill,
                    onCheck = { name, amount, currency ->
                        viewModel.checkNewEntry(name, amount, currency, isDonation = true)
                    }
                )
            }
            composable(ScreenTypes.History.route) {
                HistoryScreen(
                    history = history,
                    onEdit = viewModel::editEntry,
                    onDelete = ::deleteEntry,
                    onAttachmentSaved = viewModel::setAttachment,
                    onAddEntry = { navController.openTab(ScreenTypes.Income) },
                    originalOf = viewModel::original,
                    notice = ratesNotice,
                    showMissingReceipts = missingReceipts,
                    onMissingReceiptsShown = viewModel::consumeMissingReceipts
                )
            }
        }
    }
}
