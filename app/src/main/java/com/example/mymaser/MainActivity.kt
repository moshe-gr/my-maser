package com.example.mymaser

import android.content.Context
import android.content.res.Configuration.ORIENTATION_PORTRAIT
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.lifecycleScope
import com.example.mymaser.gui.components.TotalMaserView
import com.example.mymaser.gui.screens.MainScreen
import com.example.mymaser.history.HistoryDb
import com.example.mymaser.history.HistoryRepository.Companion.historyDao
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private const val totalMaserTXT = "total maser"
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "my_maser_prefs")

class MainActivity : AppCompatActivity() {
    private var totalMaser by mutableFloatStateOf(0F)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        historyDao = HistoryDb.getInstance(this).getHistoryDao()
        dataStore.data
            .map { totalMaser = it[floatPreferencesKey(totalMaserTXT)] ?: 0F }
            .launchIn(lifecycleScope)
        setContent {
            if (resources.configuration.orientation == ORIENTATION_PORTRAIT) {
                MainScreen(totalMaser) { edit ->
                    lifecycleScope.launch {
                        dataStore.edit {
                            it[floatPreferencesKey(totalMaserTXT)] = totalMaser + edit
                        }
                    }
                }
            } else {
                Box(modifier = Modifier
                    .fillMaxSize()
                    .background(color = colorResource(id = R.color.bg_color)), contentAlignment = Alignment.Center) {
                    TotalMaserView(totalMaser)
                }
            }
        }
    }
}