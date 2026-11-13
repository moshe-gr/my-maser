package com.example.mymaser.gui.theme

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.example.mymaser.gui.LocalDisplayCurrency
import com.example.mymaser.settings.Appearance
import com.example.mymaser.settings.ThemeMode
import com.example.mymaser.settings.appearanceFlow
import com.example.mymaser.settings.applyThemeMode

private val LightNavigationScrim = Color.argb(0xE6, 0xFF, 0xFF, 0xFF)
private val DarkNavigationScrim = Color.argb(0x80, 0x1B, 0x1B, 0x1B)

/**
 * Applies the user's theme and currency choices. Draws nothing until they load, so the app
 * never flashes the wrong theme on start.
 */
@Composable
fun MaserAppTheme(
    onLoaded: () -> Unit = {},
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val appearance: Appearance? by remember { context.appearanceFlow() }
        .collectAsState(initial = null)
    val loaded = appearance ?: return

    val darkTheme = when (loaded.themeMode) {
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
        ThemeMode.System -> isSystemInDarkTheme()
    }

    LaunchedEffect(loaded.themeMode) { context.applyThemeMode(loaded.themeMode) }

    val activity = LocalActivity.current as? ComponentActivity
    LaunchedEffect(darkTheme) {
        activity?.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
            navigationBarStyle = SystemBarStyle.auto(LightNavigationScrim, DarkNavigationScrim) {
                darkTheme
            }
        )
    }
    LaunchedEffect(Unit) { onLoaded() }

    MyMaserTheme(darkTheme = darkTheme) {
        CompositionLocalProvider(LocalDisplayCurrency provides loaded.displayCurrency, content = content)
    }
}
