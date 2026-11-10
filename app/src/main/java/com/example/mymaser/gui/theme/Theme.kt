package com.example.mymaser.gui.theme

import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.tooling.preview.Preview

private val LocalMaserColors = staticCompositionLocalOf { LightMaserColors }

val MaterialTheme.maserColors: MaserColors
    @Composable
    @ReadOnlyComposable
    get() = LocalMaserColors.current

@Composable
fun MyMaserTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalMaserColors provides if (darkTheme) DarkMaserColors else LightMaserColors
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = MaserTypography,
            shapes = MaserShapes,
            content = content
        )
    }
}

@Composable
fun MaserPreview(content: @Composable () -> Unit) {
    MyMaserTheme {
        Surface(color = MaterialTheme.colorScheme.background, content = content)
    }
}

@Preview(name = "Light", showBackground = true)
@Preview(
    name = "Dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL
)
@Preview(name = "Hebrew", showBackground = true, locale = "iw-rIL")
@Preview(name = "Large font", showBackground = true, fontScale = 2f)
annotation class ScreenPreviews
