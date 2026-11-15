package com.nudge.edge

import com.nudge.edge.ui.components.computeHudMenuExtraDp
import com.nudge.edge.ui.components.HUD_MENU_BUTTON_GAP_DP
import android.view.Surface
import com.nudge.edge.data.VolumeController
import com.nudge.edge.model.EdgeOrientationMapper
import com.nudge.edge.model.EdgeSide
import com.nudge.edge.model.NudgeSettings
import com.nudge.edge.model.ScreenEdge
import com.nudge.edge.ui.components.HUD_CUTOUT_BEZEL_PAD_DP
import com.nudge.edge.ui.components.HUD_CUTOUT_FILLET_TAIL_DP
import com.nudge.edge.ui.components.HUD_CUTOUT_INNER_PAD_DP
import com.nudge.edge.ui.components.HUD_CUTOUT_PILL_START_OFFSET_DP
import com.nudge.edge.ui.components.computeHudCutoutThicknessDp
import com.nudge.edge.ui.components.computeHudDrawnPillSpanDp
import com.nudge.edge.ui.components.computeHudOuterSpanDp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NudgeSettingsTest {

    @Test
    fun defaultsMatchSpecification() {
        val defaults = NudgeSettings()
        assertEquals(EdgeSide.RIGHT, defaults.hudEdgeSide)
        assertEquals(155f, defaults.hudHeightDp, 0.001f)
        assertEquals(45f, defaults.hudWidthDp, 0.001f)
        assertEquals(15f, defaults.hudCornerRadiusDp, 0.001f)
        assertEquals(1800L, defaults.hudAutoHideMs)
        assertTrue(defaults.enableVolumeSliderHud)
        assertFalse(defaults.showSystemPanelWhenHudOff)
        assertEquals(7, defaults.volumeStep)
        assertFalse(defaults.floatingMode)
        assertEquals(0f, defaults.hudEdgeInsetDp, 0.001f)
        assertTrue(defaults.enableSliderHudInLandscape)
        assertEquals(ScreenEdge.RIGHT, defaults.landscapeHudEdgeSide)
        assertEquals(0.50f, defaults.landscapeHudYFraction, 0.001f)
        assertTrue(defaults.showSystemPanelInLandscape)
        assertTrue(defaults.hapticsEnabled)
        assertFalse(defaults.lightMode)
    }

    @Test
    fun clampingRespectsAllBounds() {
        assertEquals(0f, NudgeSettings.clampFraction(-0.4f), 0.0001f)
        assertEquals(1f, NudgeSettings.clampFraction(1.4f), 0.0001f)
        assertEquals(1, NudgeSettings.clampVolumeStep(0))
        assertEquals(10, NudgeSettings.clampVolumeStep(25))
        assertEquals(100f, NudgeSettings.clampHudHeight(50f), 0.001f)
        assertEquals(260f, NudgeSettings.clampHudHeight(420f), 0.001f)
        assertEquals(28f, NudgeSettings.clampHudWidth(16f), 0.001f)
        assertEquals(64f, NudgeSettings.clampHudWidth(90f), 0.001f)
        assertEquals(0f, NudgeSettings.clampHudCornerRadius(-4f), 0.001f)
        assertEquals(32f, NudgeSettings.clampHudCornerRadius(45f), 0.001f)
        assertEquals(0f, NudgeSettings.clampHudEdgeInset(-4f), 0.001f)
        assertEquals(48f, NudgeSettings.clampHudEdgeInset(80f), 0.001f)
        assertEquals(250L, NudgeSettings.clampAutoHideMs(100L))
        assertEquals(5000L, NudgeSettings.clampAutoHideMs(9000L))
    }

    @Test
    fun oneDpStepAdjustsFractionAccurately() {
        val screenHeightDp = 800f
        val start = 0.5f
        val up = NudgeSettings.stepFractionByDp(start, 1f, screenHeightDp)
        val down = NudgeSettings.stepFractionByDp(start, -1f, screenHeightDp)
        assertEquals(0.5f + 1f / 800f, up, 0.00001f)
        assertEquals(0.5f - 1f / 800f, down, 0.00001f)
    }

    @Test
    fun physicalEdgeMapsAcrossAllRotations() {
        assertEquals(
            ScreenEdge.RIGHT,
            EdgeOrientationMapper.resolvePhysicalEdge(EdgeSide.RIGHT, Surface.ROTATION_0)
        )
        assertEquals(
            ScreenEdge.LEFT,
            EdgeOrientationMapper.resolvePhysicalEdge(EdgeSide.LEFT, Surface.ROTATION_0)
        )
        assertEquals(
            ScreenEdge.TOP,
            EdgeOrientationMapper.resolvePhysicalEdge(EdgeSide.RIGHT, Surface.ROTATION_90)
        )
        assertEquals(
            ScreenEdge.BOTTOM,
            EdgeOrientationMapper.resolvePhysicalEdge(EdgeSide.LEFT, Surface.ROTATION_90)
        )
        assertEquals(
            ScreenEdge.LEFT,
            EdgeOrientationMapper.resolvePhysicalEdge(EdgeSide.RIGHT, Surface.ROTATION_180)
        )
        assertEquals(
            ScreenEdge.RIGHT,
            EdgeOrientationMapper.resolvePhysicalEdge(EdgeSide.LEFT, Surface.ROTATION_180)
        )
        assertEquals(
            ScreenEdge.BOTTOM,
            EdgeOrientationMapper.resolvePhysicalEdge(EdgeSide.RIGHT, Surface.ROTATION_270)
        )
        assertEquals(
            ScreenEdge.TOP,
            EdgeOrientationMapper.resolvePhysicalEdge(EdgeSide.LEFT, Surface.ROTATION_270)
        )
    }

    @Test
    fun landscapeAndSystemPanelFallbackRulesWorkCorrectly() {
        val bothOffWithSystemPanel = NudgeSettings(
            enableSliderHudInLandscape = false,
            showSystemPanelInLandscape = true
        )
        assertFalse(EdgeOrientationMapper.shouldConsumeVolumeKey(isLandscape = true, bothOffWithSystemPanel))
        assertTrue(EdgeOrientationMapper.shouldShowSystemVolumePanel(isLandscape = true, bothOffWithSystemPanel))

        val bothOffSilent = NudgeSettings(
            enableSliderHudInLandscape = false,
            showSystemPanelInLandscape = false
        )
        assertTrue(EdgeOrientationMapper.shouldConsumeVolumeKey(isLandscape = true, bothOffSilent))
        assertFalse(EdgeOrientationMapper.shouldShowSystemVolumePanel(isLandscape = true, bothOffSilent))

        val hudOffPortraitSilent = NudgeSettings(
            enableVolumeSliderHud = false,
            showSystemPanelWhenHudOff = false
        )
        assertTrue(EdgeOrientationMapper.shouldConsumeVolumeKey(isLandscape = false, hudOffPortraitSilent))
        assertFalse(EdgeOrientationMapper.shouldShowSliderHud(isLandscape = false, hudOffPortraitSilent))
        assertFalse(EdgeOrientationMapper.shouldShowSystemVolumePanel(isLandscape = false, hudOffPortraitSilent))

        val hudOffPortraitSystem = NudgeSettings(
            enableVolumeSliderHud = false,
            showSystemPanelWhenHudOff = true
        )
        assertTrue(EdgeOrientationMapper.shouldShowSystemVolumePanel(isLandscape = false, hudOffPortraitSystem))
    }

    @Test
    fun axisTranslationAcrossRotationsComputesCorrectly() {
        assertEquals(0.25f, EdgeOrientationMapper.resolveAxisFraction(0.25f, Surface.ROTATION_90), 0.0001f)
        assertEquals(0.75f, EdgeOrientationMapper.resolveAxisFraction(0.25f, Surface.ROTATION_270), 0.0001f)
        assertEquals(0.75f, EdgeOrientationMapper.resolveAxisFraction(0.25f, Surface.ROTATION_180), 0.0001f)

        val xPos = EdgeOrientationMapper.computeHudTopOrLeftPx(
            settings = NudgeSettings(landscapeHudYFraction = 0.25f),
            rotation = Surface.ROTATION_90,
            axisLengthPx = 2000f,
            elementSpanPx = 100
        )
        assertEquals(450, xPos)
    }

    @Test
    fun dynamicDeviceVolumeScaleSteppingWorksAcrossRanges() {
        assertEquals(8, VolumeController.computeSteppedVolume(5, 1, 3, 0, 15))
        assertEquals(15, VolumeController.computeSteppedVolume(13, 1, 5, 0, 15))
        assertEquals(0, VolumeController.computeSteppedVolume(2, -1, 5, 0, 15))

        assertEquals(77, VolumeController.computeSteppedVolume(70, 1, 7, 0, 150))
        assertEquals(150, VolumeController.computeSteppedVolume(146, 1, 7, 0, 150))
        assertEquals(65, VolumeController.computeSteppedVolume(72, -1, 7, 0, 150))

        assertEquals(55, VolumeController.computeSteppedVolume(50, 1, 5, 0, 100))
    }

    @Test
    fun hudFollowsSelectedEdgeInLandscapeOrientation() {
        val rightLandscape = NudgeSettings(
            enableSliderHudInLandscape = true,
            landscapeHudEdgeSide = ScreenEdge.RIGHT
        )
        assertEquals(
            ScreenEdge.RIGHT,
            EdgeOrientationMapper.resolveHudScreenEdge(rightLandscape, Surface.ROTATION_90)
        )
        assertEquals(
            ScreenEdge.RIGHT,
            EdgeOrientationMapper.resolveHudScreenEdge(rightLandscape, Surface.ROTATION_270)
        )

        val leftLandscape = rightLandscape.copy(landscapeHudEdgeSide = ScreenEdge.LEFT)
        assertEquals(
            ScreenEdge.LEFT,
            EdgeOrientationMapper.resolveHudScreenEdge(leftLandscape, Surface.ROTATION_90)
        )
        assertEquals(
            ScreenEdge.LEFT,
            EdgeOrientationMapper.resolveHudScreenEdge(leftLandscape, Surface.ROTATION_270)
        )

        val topLandscape = rightLandscape.copy(landscapeHudEdgeSide = ScreenEdge.TOP)
        assertEquals(
            ScreenEdge.TOP,
            EdgeOrientationMapper.resolveHudScreenEdge(topLandscape, Surface.ROTATION_90)
        )
        assertEquals(
            ScreenEdge.TOP,
            EdgeOrientationMapper.resolveHudScreenEdge(topLandscape, Surface.ROTATION_270)
        )

        val bottomLandscape = rightLandscape.copy(landscapeHudEdgeSide = ScreenEdge.BOTTOM)
        assertEquals(
            ScreenEdge.BOTTOM,
            EdgeOrientationMapper.resolveHudScreenEdge(bottomLandscape, Surface.ROTATION_90)
        )
        assertEquals(
            ScreenEdge.BOTTOM,
            EdgeOrientationMapper.resolveHudScreenEdge(bottomLandscape, Surface.ROTATION_270)
        )
    }

    @Test
    fun edgeMergedHudCutoutUsesZeroBezelPadAndCompactFillet() {
        assertEquals(0f, HUD_CUTOUT_BEZEL_PAD_DP, 0.001f)
        assertEquals(6.0f, HUD_CUTOUT_INNER_PAD_DP, 0.001f)
        assertEquals(22f, HUD_CUTOUT_FILLET_TAIL_DP, 0.001f)
        assertEquals(28f, HUD_CUTOUT_PILL_START_OFFSET_DP, 0.001f)

        val defaults = NudgeSettings()
        val drawnSpanDp = computeHudDrawnPillSpanDp(defaults.hudHeightDp)
        assertEquals(defaults.hudWidthDp + 6.0f, computeHudCutoutThicknessDp(defaults.hudWidthDp, defaults.hudEdgeInsetDp), 0.001f)
        assertEquals(defaults.hudWidthDp + 12f + 6.0f, computeHudCutoutThicknessDp(defaults.hudWidthDp, 12f), 0.001f)
        assertEquals(drawnSpanDp + (22f + 6.0f) * 2f, computeHudOuterSpanDp(defaults.hudHeightDp), 0.001f)
    }

    @Test
    fun menuButtonAddsExtraSpanOnlyWhenShown() {
        assertEquals(0f, computeHudMenuExtraDp(45f, false), 0.001f)
        assertEquals(45f + HUD_MENU_BUTTON_GAP_DP, computeHudMenuExtraDp(45f, true), 0.001f)
        val base = computeHudOuterSpanDp(155f)
        assertEquals(base + 53f, computeHudOuterSpanDp(155f, 53f), 0.001f)
        assertTrue(NudgeSettings().showMenuButton)
    }
}
