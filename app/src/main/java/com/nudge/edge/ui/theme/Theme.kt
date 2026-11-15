package com.nudge.edge.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// True-black AMOLED surfaces, layered the way One UI does it: a pure black page,
// near-black panes, and dark-grey cards that step up slightly when raised or selected.
// Accent colours still come from the system (wallpaper / Samsung palette).
private val AmoledBlack = Color(0xFF000000)
private val AmoledPane = Color(0xFF080809)
private val AmoledCard = Color(0xFF1A1A1D)
private val AmoledRaised = Color(0xFF242428)
private val AmoledHighest = Color(0xFF2E2E33)
private val AmoledOutline = Color(0xFF3A3A40)

private fun ColorScheme.asAmoled(): ColorScheme = copy(
    background = AmoledBlack,
    surface = AmoledBlack,
    surfaceDim = AmoledBlack,
    surfaceBright = AmoledHighest,
    surfaceContainerLowest = AmoledBlack,
    surfaceContainerLow = AmoledPane,
    surfaceContainer = AmoledCard,
    surfaceContainerHigh = AmoledRaised,
    surfaceContainerHighest = AmoledHighest,
    surfaceVariant = AmoledCard,
    outlineVariant = AmoledOutline
)

@Composable
fun NudgeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = if (darkTheme) {
        dynamicDarkColorScheme(context).asAmoled()
    } else {
        dynamicLightColorScheme(context)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
