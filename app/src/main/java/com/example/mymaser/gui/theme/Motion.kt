package com.example.mymaser.gui.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn

object MaserMotion {
    private const val FADE_OUT_MS = 90
    private const val FADE_IN_MS = 210
    private const val COUNT_MS = 600
    private const val TAB_INITIAL_SCALE = 0.96f

    val tabEnter: EnterTransition =
        fadeIn(tween(FADE_IN_MS, FADE_OUT_MS, LinearOutSlowInEasing)) +
            scaleIn(tween(FADE_IN_MS, FADE_OUT_MS, LinearOutSlowInEasing), TAB_INITIAL_SCALE)

    val tabExit: ExitTransition = fadeOut(tween(FADE_OUT_MS, easing = FastOutLinearInEasing))

    val totalCount: AnimationSpec<Float> = tween(COUNT_MS, easing = LinearOutSlowInEasing)
}
