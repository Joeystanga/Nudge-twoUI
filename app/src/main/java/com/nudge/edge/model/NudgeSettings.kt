package com.nudge.edge.model

enum class EdgeSide {
    LEFT,
    RIGHT
}

enum class ScreenEdge {
    LEFT,
    RIGHT,
    TOP,
    BOTTOM
}

enum class HudPaletteId {
    CLASSIC,
    MONO,
    AURORA,
    LAGOON,
    EMBER,
    SAKURA
}

enum class HudTextureId {
    NONE,
    ACRYLIC,
    MESH
}

enum class PreviewTarget {
    NONE,
    HUD
}

data class NudgeSettings(
    val enableVolumeSliderHud: Boolean = true,
    val showSystemPanelWhenHudOff: Boolean = false,
    val volumeStep: Int = 7,
    val hudEdgeSide: EdgeSide = EdgeSide.RIGHT,
    val hudYFraction: Float = 0.34f,
    val hudHeightDp: Float = 155f,
    val hudWidthDp: Float = 45f,
    val hudCornerRadiusDp: Float = 15f,
    val hudAutoHideMs: Long = 1800L,
    val enableSliderHudInLandscape: Boolean = true,
    val landscapeHudEdgeSide: ScreenEdge = ScreenEdge.RIGHT,
    val landscapeHudYFraction: Float = 0.50f,
    val showSystemPanelInLandscape: Boolean = true,
    val floatingMode: Boolean = false,
    val showMenuButton: Boolean = true,
    val hudPalette: HudPaletteId = HudPaletteId.CLASSIC,
    val hudUseGradient: Boolean = true,
    val hudAnimateGradient: Boolean = false,
    val hudColorIntensity: Float = 0.65f,
    val hudTexture: HudTextureId = HudTextureId.NONE,
    val hudBorderWidthDp: Float = 0f,
    val hudEdgeInsetDp: Float = 0f,
    val hapticsEnabled: Boolean = true,
    val lightMode: Boolean = false
) {
    companion object {
        const val MIN_VOLUME_STEP = 1
        const val MAX_VOLUME_STEP = 10
        const val MIN_HUD_HEIGHT_DP = 100f
        const val MAX_HUD_HEIGHT_DP = 260f
        const val MIN_HUD_WIDTH_DP = 28f
        const val MAX_HUD_WIDTH_DP = 64f
        const val MIN_HUD_CORNER_RADIUS_DP = 0f
        const val MAX_HUD_CORNER_RADIUS_DP = 32f
        const val MIN_HUD_EDGE_INSET_DP = 0f
        const val MAX_HUD_EDGE_INSET_DP = 48f
        const val MIN_AUTO_HIDE_MS = 250L
        const val MAX_AUTO_HIDE_MS = 5000L
        const val AUTO_HIDE_STEP_MS = 50L
        const val MIN_COLOR_INTENSITY = 0.30f
        const val MAX_COLOR_INTENSITY = 1.0f
        const val MIN_HUD_BORDER_DP = 0f
        const val MAX_HUD_BORDER_DP = 3f

        fun clampFraction(value: Float): Float = value.coerceIn(0f, 1f)

        fun clampVolumeStep(value: Int): Int =
            value.coerceIn(MIN_VOLUME_STEP, MAX_VOLUME_STEP)

        fun clampHudHeight(value: Float): Float =
            value.coerceIn(MIN_HUD_HEIGHT_DP, MAX_HUD_HEIGHT_DP)

        fun clampHudWidth(value: Float): Float =
            value.coerceIn(MIN_HUD_WIDTH_DP, MAX_HUD_WIDTH_DP)

        fun clampHudCornerRadius(value: Float): Float =
            value.coerceIn(MIN_HUD_CORNER_RADIUS_DP, MAX_HUD_CORNER_RADIUS_DP)

        fun clampHudEdgeInset(value: Float): Float =
            value.coerceIn(MIN_HUD_EDGE_INSET_DP, MAX_HUD_EDGE_INSET_DP)

        fun clampColorIntensity(value: Float): Float =
            value.coerceIn(MIN_COLOR_INTENSITY, MAX_COLOR_INTENSITY)

        fun clampHudBorder(value: Float): Float =
            value.coerceIn(MIN_HUD_BORDER_DP, MAX_HUD_BORDER_DP)

        fun clampAutoHideMs(value: Long): Long =
            value.coerceIn(MIN_AUTO_HIDE_MS, MAX_AUTO_HIDE_MS)

        fun stepFractionByDp(
            currentFraction: Float,
            deltaDp: Float,
            screenHeightDp: Float
        ): Float {
            if (screenHeightDp <= 0f) return clampFraction(currentFraction)
            val deltaFraction = deltaDp / screenHeightDp
            return clampFraction(currentFraction + deltaFraction)
        }
    }
}
