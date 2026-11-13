package com.example.mymaser

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.example.mymaser.settings.mirroredThemeMode

class MaserApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(mirroredThemeMode().nightMode)
    }
}
