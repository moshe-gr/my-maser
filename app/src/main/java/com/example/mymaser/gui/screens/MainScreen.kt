package com.example.mymaser.gui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
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
import com.example.mymaser.ai.ChatViewModel
import com.example.mymaser.ai.ImportViewModel
import com.example.mymaser.gui.ScreenTypes
import com.example.mymaser.gui.components.AnimatedPresence
import com.example.mymaser.gui.components.DueCards
import com.example.mymaser.gui.components.EmailImportCard
import com.example.mymaser.gui.components.EntryForm
import com.example.mymaser.gui.components.EntryTypeSelector
import com.example.mymaser.gui.components.ForecastLine
import com.example.mymaser.gui.components.ImportButton
import com.example.mymaser.gui.components.ImportReviewSheet
import com.example.mymaser.gui.components.QuickAddField
import com.example.mymaser.gui.components.RatesNotice
import com.example.mymaser.gui.components.ReminderPromptCard
import com.example.mymaser.gui.theme.MaserMotion
import com.example.mymaser.history.History
import com.example.mymaser.history.HistoryViewModel
import com.example.mymaser.history.NewEntry
import com.example.mymaser.history.toPrefill
import com.example.mymaser.reminders.AppDestination
import com.example.mymaser.settings.SettingsActivity
import kotlinx.coroutines.launch

/** The add form is pushed over a tab rather than being a tab itself. */
private const val ADD_ROUTE = "add"
private const val FORM_SLIDE_MS = 300

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
fun MainScreen(
    viewModel: HistoryViewModel = viewModel(),
    importViewModel: ImportViewModel = viewModel(),
    chatViewModel: ChatViewModel = viewModel()
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val haptics = LocalHapticFeedback.current
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val onForm = destination?.route == ADD_ROUTE
    val current = ScreenTypes.entries.firstOrNull { destination.isOn(it) } ?: ScreenTypes.Home
    var addingDonation by rememberSaveable { mutableStateOf(true) }

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
    val reminderPrompt by viewModel.reminderPrompt.collectAsStateWithLifecycle()
    val forecast by viewModel.forecast.collectAsStateWithLifecycle()
    val importState by importViewModel.state.collectAsStateWithLifecycle()
    val aiEnabled by importViewModel.aiEnabled.collectAsStateWithLifecycle()
    val pendingEmails by importViewModel.pendingEmails.collectAsStateWithLifecycle()

    val undoLabel = stringResource(R.string.undo)
    val savedMessage = stringResource(R.string.information_saved_successfully)
    val deletedMessage = stringResource(R.string.entry_deleted)

    fun openSettings() = context.startActivity(Intent(context, SettingsActivity::class.java))

    fun openForm(isDonation: Boolean) {
        addingDonation = isDonation
        navController.navigate(ADD_ROUTE) { launchSingleTop = true }
    }

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

    fun saveEntry(
        name: String,
        amount: Float,
        currency: String,
        isDonation: Boolean,
        isTaxOnly: Boolean = false,
        taxCredit: Boolean = false
    ) {
        viewModel.addEntry(name, amount, currency, isDonation, isTaxOnly, taxCredit) { saved ->
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            offerUndo(savedMessage) { viewModel.deleteEntry(saved) }
        }
    }

    val resources = LocalResources.current

    fun saveImported(entries: List<NewEntry>) {
        importViewModel.finishReview(entries)
        viewModel.addEntries(entries) { saved ->
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            offerUndo(resources.getQuantityString(R.plurals.import_saved, saved.size, saved.size)) {
                saved.forEach(viewModel::deleteEntry)
            }
        }
    }

    fun deleteEntry(entry: History) {
        val removed = viewModel.deleteEntry(entry)
        offerUndo(deletedMessage) { viewModel.restoreEntry(removed) }
    }

    LaunchedEffect(prefill) {
        prefill?.let { openForm(it.isDonation) }
    }

    LaunchedEffect(requestedDestination) {
        when (requestedDestination) {
            AppDestination.Home -> navController.openTab(ScreenTypes.Home)
            AppDestination.History -> navController.openTab(ScreenTypes.History)
            AppDestination.Donation -> openForm(isDonation = true)
            null -> return@LaunchedEffect
        }
        viewModel.consumeDestination()
    }

    LaunchedEffect(Unit) {
        importViewModel.prefills.collect(viewModel::requestPrefill)
    }

    val dueCards: @Composable () -> Unit = {
        AnimatedPresence(pendingEmails.size.takeIf { it > 0 }) { count ->
            EmailImportCard(
                emails = count,
                onReview = importViewModel::reviewEmails,
                onDismiss = importViewModel::dismissEmails
            )
        }
        QuickAddField(onSubmit = importViewModel::quickAdd)
        DueCards(
            predictions = predictions,
            onLog = { saveEntry(it.name, it.amount, it.currency, it.isDonation, it.isTaxOnly, it.taxCredit) },
            onOpen = { viewModel.requestPrefill(it.toPrefill()) },
            onSkip = viewModel::dismissPrediction
        )
        AnimatedPresence(reminderPrompt) { source ->
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

    ImportReviewSheet(
        state = importState,
        onCheck = { viewModel.checkNewEntry(it.name, it.amount, it.currency, it.isDonation, it.timeStamp) },
        onConfirm = ::saveImported,
        onRetry = importViewModel::retry,
        onOpenSettings = {
            importViewModel.dismiss()
            openSettings()
        },
        onDismiss = importViewModel::dismiss
    )

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    LaunchedEffect(destination?.route) { scrollBehavior.state.contentOffset = 0f }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CenterAlignedTopAppBar(
                scrollBehavior = scrollBehavior,
                title = {
                    Text(
                        stringResource(
                            when {
                                onForm -> if (addingDonation) R.string.add_donation else R.string.add_income
                                current == ScreenTypes.Home -> R.string.app_name
                                current == ScreenTypes.Ask -> R.string.chat_title
                                else -> current.label
                            }
                        )
                    )
                },
                navigationIcon = {
                    if (onForm) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    }
                },
                actions = {
                    if (!onForm) {
                        IconButton(onClick = ::openSettings) {
                            Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings))
                        }
                    }
                }
            )
        },
        bottomBar = {
            AnimatedVisibility(visible = !onForm, enter = MaserMotion.expand, exit = MaserMotion.collapse) {
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
            }
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = !onForm && current == ScreenTypes.History,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                ExtendedFloatingActionButton(
                    onClick = { openForm(isDonation = true) },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.add_entry)) }
                )
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
            exitTransition = {
                if (targetState.destination.route == ADD_ROUTE) {
                    ExitTransition.KeepUntilTransitionsFinished
                } else {
                    MaserMotion.tabExit
                }
            },
            popEnterTransition = {
                if (initialState.destination.route == ADD_ROUTE) EnterTransition.None else MaserMotion.tabEnter
            },
            popExitTransition = { MaserMotion.tabExit }
        ) {
            composable(ScreenTypes.Home.route) {
                HomeScreen(
                    history = history,
                    totalMaser = totalMaser,
                    lastShownTotal = viewModel.lastShownTotal,
                    onTotalShown = { viewModel.lastShownTotal = it },
                    onAddIncome = { openForm(isDonation = false) },
                    onAddDonation = { openForm(isDonation = true) },
                    maserPercentage = maserPercentage,
                    notice = homeNotice,
                    dueCards = dueCards
                )
            }
            composable(ScreenTypes.History.route) {
                HistoryScreen(
                    history = history,
                    onEdit = viewModel::editEntry,
                    onDelete = ::deleteEntry,
                    onAddEntry = { openForm(isDonation = false) },
                    onTaxCreditForName = viewModel::setTaxCreditForName,
                    originalOf = viewModel::original,
                    notice = ratesNotice
                )
            }
            composable(ScreenTypes.Ask.route) {
                val messages by chatViewModel.messages.collectAsStateWithLifecycle()
                val thinking by chatViewModel.thinking.collectAsStateWithLifecycle()
                ChatScreen(
                    messages = messages,
                    thinking = thinking,
                    onAsk = { chatViewModel.ask(it, viewModel.chatData()) },
                    onRetry = { chatViewModel.retry(viewModel.chatData()) },
                    onOpenSettings = ::openSettings,
                    enabled = aiEnabled
                )
            }
            composable(
                ADD_ROUTE,
                enterTransition = {
                    slideIntoContainer(SlideDirection.Up, tween(FORM_SLIDE_MS, easing = FastOutSlowInEasing)) +
                        fadeIn(tween(FORM_SLIDE_MS))
                },
                popExitTransition = {
                    slideOutOfContainer(SlideDirection.Down, tween(FORM_SLIDE_MS, easing = FastOutSlowInEasing)) +
                        fadeOut(tween(FORM_SLIDE_MS))
                }
            ) {
                val isDonation = addingDonation
                key(isDonation) {
                    EntryForm(
                        isDonation = isDonation,
                        names = if (isDonation) donationNames else incomeNames,
                        lastEntry = history?.firstOrNull { it.isDonation == isDonation },
                        onSuggestionPicked = { viewModel.suggestedAmount(it, isDonation) },
                        onSave = { name, amount, currency, isTaxOnly, taxCredit ->
                            saveEntry(name, amount, currency, isDonation, isTaxOnly, taxCredit)
                            navController.popBackStack()
                        },
                        maserPercentage = maserPercentage,
                        taxCreditFor = viewModel::lastTaxCredit,
                        prefill = prefill,
                        onPrefillConsumed = viewModel::consumePrefill,
                        onCheck = { name, amount, currency ->
                            viewModel.checkNewEntry(name, amount, currency, isDonation)
                        },
                        header = {
                            EntryTypeSelector(isDonation = isDonation, onChange = { addingDonation = it })
                            if (aiEnabled) ImportButton(onDocuments = importViewModel::readUris)
                        }
                    )
                }
            }
        }
    }
}
