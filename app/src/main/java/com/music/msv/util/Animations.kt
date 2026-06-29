package com.music.msv.util

import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically

object Animations {
    const val PAGE_FLIP_MS = 160
    const val UI_TOGGLE_MS = 280
    const val PANEL_SLIDE_MS = 220
    const val SHADE_FADE_MS = 180
    const val SPINNER_MS = 900
    const val THEME_CROSSFADE_MS = 300
    const val BUTTON_PRESS_MS = 180

    val pageFlipOutNext = slideOutHorizontally(
        animationSpec = tween(PAGE_FLIP_MS, easing = LinearEasing),
        targetOffsetX = { -it }
    ) + fadeOut(tween(PAGE_FLIP_MS, easing = LinearEasing))

    val pageFlipOutPrev = slideOutHorizontally(
        animationSpec = tween(PAGE_FLIP_MS, easing = LinearEasing),
        targetOffsetX = { it }
    ) + fadeOut(tween(PAGE_FLIP_MS, easing = LinearEasing))

    val pageFlipIn = fadeIn(tween(PAGE_FLIP_MS, easing = LinearEasing))

    val topbarSlideIn = slideInVertically(tween(UI_TOGGLE_MS)) { -it } + fadeIn(tween(UI_TOGGLE_MS))
    val topbarSlideOut = slideOutVertically(tween(UI_TOGGLE_MS)) { -it } + fadeOut(tween(UI_TOGGLE_MS))

    val footerSlideIn = slideInVertically(tween(UI_TOGGLE_MS)) { it } + fadeIn(tween(UI_TOGGLE_MS))
    val footerSlideOut = slideOutVertically(tween(UI_TOGGLE_MS)) { it } + fadeOut(tween(UI_TOGGLE_MS))

    val panelSlideIn = slideInHorizontally(tween(PANEL_SLIDE_MS)) { it / 3 } + fadeIn(tween(PANEL_SLIDE_MS))
    val panelSlideOut = slideOutHorizontally(tween(PANEL_SLIDE_MS)) { it / 3 } + fadeOut(tween(PANEL_SLIDE_MS))
}
