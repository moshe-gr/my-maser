package com.example.mymaser

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.lifecycleScope
import com.example.mymaser.ai.ImportViewModel
import com.example.mymaser.ai.isDocumentShare
import com.example.mymaser.gmail.GmailScheduler
import com.example.mymaser.gui.screens.MainScreen
import com.example.mymaser.gui.theme.MaserAppTheme
import com.example.mymaser.history.HistoryViewModel
import com.example.mymaser.reminders.ReminderScheduler
import com.example.mymaser.reminders.createNotificationChannels
import com.example.mymaser.reminders.toDeepLink
import kotlinx.coroutines.launch

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "my_maser_prefs")

class MainActivity : AppCompatActivity() {
    private var appearanceLoaded = false
    private val historyViewModel: HistoryViewModel by viewModels()
    private val importViewModel: ImportViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setKeepOnScreenCondition { !appearanceLoaded }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        createNotificationChannels(this)
        lifecycleScope.launch {
            ReminderScheduler.sync(applicationContext)
            GmailScheduler.sync(applicationContext)
        }
        if (savedInstanceState == null) handleLink(intent)
        setContent {
            MaserAppTheme(onLoaded = { appearanceLoaded = true }) {
                MainScreen(historyViewModel, importViewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleLink(intent)
    }

    private fun handleLink(intent: Intent?) {
        if (intent == null) return
        if (intent.isDocumentShare()) {
            importViewModel.readShare(intent)
        } else {
            intent.toDeepLink()?.let(historyViewModel::openLink)
        }
    }
}
