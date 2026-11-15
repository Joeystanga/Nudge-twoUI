package com.nudge.edge.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TileMode
import com.nudge.edge.model.HudPaletteId

/**
 * Colours for the volume slider fill. The unfilled part of the bar is drawn as a dark,
 * tinted version of the same colours so the whole bar reads as one lit surface.
 */
data class HudPaletteSpec(
    val label: String,
    val stops: List<Color>,
    val flat: Color,
    val iconOnFill: Color,
    val isClassic: Boolean = false
)

private val ClassicSpec = HudPaletteSpec(
    label = "Classic",
    stops = listOf(Color.White, Color.White),
    flat = Color.White,
    iconOnFill = Color(0xFF6E6E73),
    isClassic = true
)
private val MonoSpec = HudPaletteSpec(
    label = "Mono",
    stops = listOf(Color(0xFFC4C4CE), Color(0xFF8E8E9A), Color(0xFF63636E)),
    flat = Color(0xFF9A9AA6),
    iconOnFill = Color.White
)
private val AuroraSpec = HudPaletteSpec(
    label = "Aurora",
    stops = listOf(Color(0xFFE66BE3), Color(0xFFA37CFF), Color(0xFF5FA8FF)),
    flat = Color(0xFFA37CFF),
    iconOnFill = Color.White
)
private val LagoonSpec = HudPaletteSpec(
    label = "Lagoon",
    stops = listOf(Color(0xFF38D5B8), Color(0xFF3FA3E0), Color(0xFF6577E8)),
    flat = Color(0xFF3FA3E0),
    iconOnFill = Color.White
)
private val EmberSpec = HudPaletteSpec(
    label = "Ember",
    stops = listOf(Color(0xFFFF8A4C), Color(0xFFFFC857), Color(0xFF9BE073), Color(0xFF4FD1C5)),
    flat = Color(0xFFFFB04C),
    iconOnFill = Color.White
)
private val SakuraSpec = HudPaletteSpec(
    label = "Sakura",
    stops = listOf(Color(0xFFFF9EBD), Color(0xFFFFB9A3), Color(0xFFFFD6A5)),
    flat = Color(0xFFFFA8B4),
    iconOnFill = Color.White
)

fun hudPaletteSpec(id: HudPaletteId): HudPaletteSpec = when (id) {
    HudPaletteId.CLASSIC -> ClassicSpec
    HudPaletteId.MONO -> MonoSpec
    HudPaletteId.AURORA -> AuroraSpec
    HudPaletteId.LAGOON -> LagoonSpec
    HudPaletteId.EMBER -> EmberSpec
    HudPaletteId.SAKURA -> SakuraSpec
}

/**
 * Brush for the filled part of the bar. [start] is where the fill begins (the speaker end)
 * and [end] is the far end. [shift] (0..1) slides the colours along the bar; because the
 * first colour is repeated at the end, looping 0 -> 1 is seamless.
 */
fun hudFillBrush(
    spec: HudPaletteSpec,
    useGradient: Boolean,
    start: Offset,
    end: Offset,
    shift: Float
): Brush {
    if (spec.isClassic || !useGradient || spec.stops.size < 2) return SolidColor(spec.flat)
    val dx = (end.x - start.x) * shift
    val dy = (end.y - start.y) * shift
    return Brush.linearGradient(
        colors = spec.stops + spec.stops.first(),
        start = Offset(start.x + dx, start.y + dy),
        end = Offset(end.x + dx, end.y + dy),
        tileMode = TileMode.Repeated
    )
}
