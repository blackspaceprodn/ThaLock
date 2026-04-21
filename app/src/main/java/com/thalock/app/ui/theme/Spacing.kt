package com.thalock.app.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class Spacing(
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 24.dp,
    val xl: Dp = 32.dp,
    val xxl: Dp = 48.dp,

    // Consistent screen padding
    val screenHorizontal: Dp = 20.dp,
    val screenVertical: Dp = 16.dp,

    // Card internals
    val cardPadding: Dp = 16.dp,
    val cardGap: Dp = 12.dp,

    // Section spacing
    val sectionGap: Dp = 28.dp,

    // Field spacing
    val fieldGap: Dp = 12.dp,

    // Minimum touch target
    val touchTarget: Dp = 48.dp
)

val LocalSpacing = compositionLocalOf { Spacing() }
