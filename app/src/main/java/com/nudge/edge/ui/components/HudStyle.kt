package com.nudge.edge.ui.components

import android.graphics.RuntimeShader
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.nudge.edge.model.HudPaletteId
import com.nudge.edge.model.HudTextureId

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

val HudBorderColor = Color.White.copy(alpha = 0.34f)

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

/**
 * Pulls a colour toward its own grey and slightly darkens it. [intensity] 1.0 keeps the
 * original colour; lower values give a calmer, less distracting glow.
 */
private fun softened(color: Color, intensity: Float): Color {
    val i = intensity.coerceIn(0f, 1f)
    val desaturate = (1f - i) * 0.9f
    val dim = (1f - i) * 0.25f
    val luma = 0.299f * color.red + 0.587f * color.green + 0.114f * color.blue
    fun channel(c: Float): Float = (c + (luma - c) * desaturate) * (1f - dim)
    return Color(channel(color.red), channel(color.green), channel(color.blue), color.alpha)
}

private fun baseSpec(id: HudPaletteId): HudPaletteSpec = when (id) {
    HudPaletteId.CLASSIC -> ClassicSpec
    HudPaletteId.MONO -> MonoSpec
    HudPaletteId.AURORA -> AuroraSpec
    HudPaletteId.LAGOON -> LagoonSpec
    HudPaletteId.EMBER -> EmberSpec
    HudPaletteId.SAKURA -> SakuraSpec
}

fun hudPaletteSpec(id: HudPaletteId, intensity: Float = 1f): HudPaletteSpec {
    val base = baseSpec(id)
    if (base.isClassic || intensity >= 0.999f) return base
    return base.copy(
        stops = base.stops.map { softened(it, intensity) },
        flat = softened(base.flat, intensity)
    )
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

// Fine frosted grain: random light and dark specks, a little stronger where they are lighter.
private const val ACRYLIC_AGSL = """
uniform float uIntensity;
half4 main(float2 p) {
    float n = fract(sin(dot(floor(p), float2(12.9898, 78.233))) * 43758.5453);
    float light = step(0.5, n);
    float a = abs(n - 0.5) * 2.0 * uIntensity;
    float r = a * light;
    return half4(r, r, r, a * light + a * 0.6 * (1.0 - light));
}
"""

// Woven mesh: a 45 degree grid of small dark dots.
private const val MESH_AGSL = """
uniform float uCell;
uniform float uIntensity;
half4 main(float2 p) {
    float2 q = float2(p.x + p.y, p.x - p.y) * 0.70710678 / uCell;
    float2 g = fract(q) - 0.5;
    float d = length(g);
    float dotMask = 1.0 - smoothstep(0.26, 0.40, d);
    float a = dotMask * uIntensity;
    return half4(0.0, 0.0, 0.0, a);
}
"""

/**
 * Builds the texture brush for [texture], or null for no texture. Shader creation is
 * guarded so a device that rejects the shader just shows the bar without a texture.
 */
fun createHudTextureBrush(texture: HudTextureId, cellPx: Float): Brush? {
    return runCatching {
        when (texture) {
            HudTextureId.NONE -> null
            HudTextureId.ACRYLIC -> {
                val shader = RuntimeShader(ACRYLIC_AGSL)
                shader.setFloatUniform("uIntensity", 0.30f)
                ShaderBrush(shader)
            }
            HudTextureId.MESH -> {
                val shader = RuntimeShader(MESH_AGSL)
                shader.setFloatUniform("uCell", cellPx.coerceAtLeast(2f))
                shader.setFloatUniform("uIntensity", 0.34f)
                ShaderBrush(shader)
            }
        }
    }.getOrNull()
}

/**
 * Texture and border for the bar surface. Call inside a clip to the bar's shape; the
 * border is drawn twice as wide because only its inner half stays inside the clip.
 */
fun DrawScope.drawHudSurfaceFx(
    texture: HudTextureId,
    textureBrush: Brush?,
    pillPath: Path,
    topLeft: Offset,
    size: Size,
    borderPx: Float,
    borderColor: Color
) {
    if (texture == HudTextureId.ACRYLIC) {
        val sheen = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.22f),
                Color.Transparent,
                Color.White.copy(alpha = 0.08f)
            ),
            start = topLeft,
            end = Offset(topLeft.x + size.width, topLeft.y + size.height)
        )
        drawRect(brush = sheen, topLeft = topLeft, size = size)
    }
    if (texture != HudTextureId.NONE && textureBrush != null) {
        drawRect(brush = textureBrush, topLeft = topLeft, size = size)
    }
    if (borderPx > 0f) {
        drawPath(path = pillPath, color = borderColor, style = Stroke(width = borderPx * 2f))
    }
}
