package com.example.mymaser.settings

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.mymaser.gui.theme.MyMaserTheme
import com.example.mymaser.settings.data.SettingsRepository
import com.example.mymaser.settings.gui.SettingsScreen

class SettingsActivity : AppCompatActivity() {

    private val settingsViewModel: SettingsViewModel by viewModels {
        SettingsViewModelFactory(SettingsRepository(this))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MyMaserTheme {
                SettingsScreen(settingsViewModel, onBack = ::finish)
            }
        }
    }
}
