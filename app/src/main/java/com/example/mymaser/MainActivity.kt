package com.example.mymaser

import android.content.Context
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.example.mymaser.gui.screens.MainScreen
import com.example.mymaser.gui.theme.MyMaserTheme

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "my_maser_prefs")

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MyMaserTheme {
                MainScreen()
            }
        }
    }
}
