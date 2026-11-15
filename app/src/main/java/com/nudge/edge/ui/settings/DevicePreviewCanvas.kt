package com.nudge.edge.ui.settings

import com.nudge.edge.ui.components.hudPaletteSpec
import com.nudge.edge.ui.components.hudFillBrush
import com.nudge.edge.ui.components.drawHudMenuButton
import com.nudge.edge.ui.components.computeHudMenuExtraDp
import com.nudge.edge.ui.components.HUD_MENU_BUTTON_GAP_DP
import android.view.Surface
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nudge.edge.model.EdgeOrientationMapper
import com.nudge.edge.model.NudgeSettings
import com.nudge.edge.model.PreviewTarget
import com.nudge.edge.model.ScreenEdge
import com.nudge.edge.ui.components.HUD_CUTOUT_BEZEL_PAD_DP
import com.nudge.edge.ui.components.HUD_CUTOUT_FILLET_TAIL_DP
import com.nudge.edge.ui.components.HUD_CUTOUT_INNER_PAD_DP
import com.nudge.edge.ui.components.HUD_CUTOUT_PILL_START_OFFSET_DP
import com.nudge.edge.ui.components.buildHudHousingPath
import com.nudge.edge.ui.components.computeHudCutoutThicknessDp
import com.nudge.edge.ui.components.computeHudDrawnPillSpanDp
import com.nudge.edge.ui.components.computeHudOuterSpanDp
import com.nudge.edge.ui.components.drawSpeakerGlyph

@Composable
fun DevicePreviewHeader(
    serviceActive: Boolean,
    onStatusClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pillContainerColor by animateColorAsState(
        targetValue = if (serviceActive) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else {
            MaterialTheme.colorScheme.errorContainer
        },
        label = "statusPillBg"
    )
    val pillContentColor by animateColorAsState(
        targetValue = if (serviceActive) {
            MaterialTheme.colorScheme.onTertiaryContainer
        } else {
            MaterialTheme.colorScheme.onErrorContainer
        },
        label = "statusPillFg"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Nudge",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )

        Surface(
            onClick = onStatusClick,
            shape = RoundedCornerShape(50),
            color = pillContainerColor,
            contentColor = pillContentColor
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = if (serviceActive) {
                        Icons.Rounded.CheckCircle
                    } else {
                        Icons.Rounded.AccessibilityNew
                    },
                    contentDescription = null,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = if (serviceActive) {
                        "Service Active"
                    } else {
                        "Enable Accessibility"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun DevicePreviewCanvas(
    settings: NudgeSettings,
    previewTarget: PreviewTarget,
    musicVolumeFraction: Float,
    realScreenWidthDp: Float,
    realScreenHeightDp: Float,
    displayRotation: Int,
    onPreviewTap: (PreviewTarget) -> Unit,
    modifier: Modifier = Modifier
) {
    val deviceAspect = (realScreenWidthDp / realScreenHeightDp).coerceIn(0.42f, 2.35f)
    val screenTopColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val screenBottomColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
    val frameStrokeColor = MaterialTheme.colorScheme.outline
    val activeAccent = MaterialTheme.colorScheme.primary

    val isLandscape = EdgeOrientationMapper.isLandscapeRotation(displayRotation)
    val hudAllowed = EdgeOrientationMapper.shouldShowSliderHud(isLandscape, settings)
    val hudScreenEdge = EdgeOrientationMapper.resolveHudScreenEdge(
        settings = settings,
        rotation = displayRotation
    )

    val previewFloatProgress by animateFloatAsState(
        targetValue = if (settings.floatingMode) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 480f),
        label = "previewFloatProgress"
    )
    val previewInsetDp by animateFloatAsState(
        targetValue = settings.hudEdgeInsetDp.coerceAtLeast(0f),
        animationSpec = spring(dampingRatio = 0.84f, stiffness = 480f),
        label = "previewInsetDp"
    )

    val hudPath = remember { Path() }
    val pillPath = remember { Path() }
    val speakerPath = remember { Path() }
    val screenClipPath = remember { Path() }

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .aspectRatio(deviceAspect, matchHeightConstraintsFirst = !isLandscape),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(settings, realScreenWidthDp, realScreenHeightDp, displayRotation) {
                        detectTapGestures {
                            if (hudAllowed) {
                                onPreviewTap(PreviewTarget.HUD)
                            }
                        }
                    }
            ) {
                val scale = size.height / realScreenHeightDp.coerceAtLeast(1f)
                val cornerRadiusPx = 18.dp.toPx()
                val bezelStrokePx = 3.dp.toPx()

                screenClipPath.reset()
                screenClipPath.addRoundRect(
                    RoundRect(
                        rect = Rect(0f, 0f, size.width, size.height),
                        cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
                    )
                )

                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(screenTopColor, screenBottomColor)
                    ),
                    cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
                )

                clipPath(screenClipPath) {
                    if (hudAllowed) {
                        val hudIsVertical = hudScreenEdge == ScreenEdge.LEFT ||
                            hudScreenEdge == ScreenEdge.RIGHT
                        val paletteSpec = hudPaletteSpec(settings.hudPalette)
                        val pillW = settings.hudWidthDp * scale
                        val pillSpan = computeHudDrawnPillSpanDp(settings.hudHeightDp) * scale
                        val cutoutThickness = computeHudCutoutThicknessDp(settings.hudWidthDp, previewInsetDp) * scale
                        val menuExtraDp = computeHudMenuExtraDp(settings.hudWidthDp, settings.showMenuButton)
                        val menuExtra = menuExtraDp * scale
                        val outerSpan = computeHudOuterSpanDp(settings.hudHeightDp, menuExtraDp) * scale
                        val bezelPad = (HUD_CUTOUT_BEZEL_PAD_DP + previewInsetDp) * scale
                        val innerPad = HUD_CUTOUT_INNER_PAD_DP * scale
                        val filletTail = HUD_CUTOUT_FILLET_TAIL_DP * scale
                        val pillStartOffset = HUD_CUTOUT_PILL_START_OFFSET_DP * scale
                        val pillCornerR = (settings.hudCornerRadiusDp * scale).coerceIn(
                            0f,
                            minOf(pillW, pillSpan) * 0.5f
                        )
                        val hudAxisLen = if (hudIsVertical) size.height else size.width
                        val hudCenterFraction = EdgeOrientationMapper.resolveHudAxisFraction(
                            settings = settings,
                            rotation = displayRotation
                        )
                        val hudRawCenter = hudCenterFraction * hudAxisLen
                        val hudClampedCenter = if (hudAxisLen <= outerSpan) {
                            hudAxisLen * 0.5f
                        } else {
                            hudRawCenter.coerceIn(outerSpan * 0.5f, hudAxisLen - outerSpan * 0.5f)
                        }
                        val hudStartOffset = (hudClampedCenter - outerSpan * 0.5f)
                            .coerceIn(0f, (hudAxisLen - outerSpan).coerceAtLeast(0f))

                        if (hudIsVertical) {
                            val isHudRight = hudScreenEdge == ScreenEdge.RIGHT
                            val hudBezelX = if (isHudRight) size.width else 0f
                            val inwardDir = if (isHudRight) -1f else 1f
                            val hudTop = hudStartOffset
                            val notchAlpha = (1f - previewFloatProgress) *
                                if (previewTarget == PreviewTarget.HUD) 1f else 0.85f
                            if (notchAlpha > 0.02f) {
                                val notchW = cutoutThickness * (1f - previewFloatProgress * 0.72f)
                                val notchLeft = if (isHudRight) hudBezelX - notchW else 0f
                                translate(left = notchLeft, top = hudTop) {
                                    buildHudHousingPath(
                                        path = hudPath,
                                        width = notchW,
                                        height = outerSpan,
                                        screenEdge = hudScreenEdge,
                                        pillCornerRadiusPx = pillCornerR,
                                        innerPadPx = innerPad,
                                        filletTailPx = filletTail
                                    )
                                    drawPath(path = hudPath, color = Color.Black.copy(alpha = notchAlpha))
                                }
                            }

                            val pillCenterX = hudBezelX + inwardDir * (bezelPad + pillW * 0.5f)
                            val pillLeft = pillCenterX - pillW * 0.5f
                            val pillRight = pillCenterX + pillW * 0.5f
                            val pillTop = hudTop + pillStartOffset + menuExtra
                            val pillBottom = pillTop + pillSpan

                            pillPath.reset()
                            pillPath.addRoundRect(
                                RoundRect(
                                    rect = Rect(pillLeft, pillTop, pillRight, pillBottom),
                                    cornerRadius = CornerRadius(pillCornerR, pillCornerR)
                                )
                            )
                            drawPath(path = pillPath, color = Color(0xFF2C2C2E))

                            if (settings.showMenuButton) {
                                drawHudMenuButton(
                                    left = pillLeft,
                                    top = hudTop + pillStartOffset,
                                    sizePx = pillW,
                                    cornerRadiusPx = pillCornerR,
                                    trackColor = Color(0xFF2C2C2E),
                                    dotColor = Color.White,
                                    dotRadiusPx = (pillW * 0.07f).coerceAtLeast(1f)
                                )
                            }

                            val fillTop = (pillBottom - pillSpan * musicVolumeFraction).coerceIn(pillTop, pillBottom)
                            val fillBrush = hudFillBrush(
                                spec = paletteSpec,
                                useGradient = settings.hudUseGradient,
                                start = Offset(pillCenterX, pillBottom),
                                end = Offset(pillCenterX, pillTop),
                                shift = 0f
                            )
                            clipPath(pillPath) {
                                if (!paletteSpec.isClassic) {
                                    drawRect(
                                        brush = fillBrush,
                                        topLeft = Offset(pillLeft, pillTop),
                                        size = Size(pillW, pillSpan),
                                        alpha = 0.26f
                                    )
                                }
                                if (musicVolumeFraction > 0.01f) {
                                    drawRect(
                                        brush = fillBrush,
                                        topLeft = Offset(pillLeft, fillTop),
                                        size = Size(pillW, pillBottom - fillTop)
                                    )
                                }
                                val glyphSize = (pillW * 0.45f).coerceAtLeast(3f)
                                val glyphCenter = Offset(pillCenterX, pillBottom - pillW * 0.44f)
                                if (fillTop > pillTop) {
                                    clipRect(pillLeft, pillTop, pillRight, fillTop) {
                                        drawSpeakerGlyph(
                                            center = glyphCenter,
                                            glyphSize = glyphSize,
                                            color = Color.White,
                                            volumeFraction = musicVolumeFraction,
                                            pathBuffer = speakerPath,
                                            cutoutColor = Color(0xFF2C2C2E)
                                        )
                                    }
                                }
                                if (fillTop < pillBottom) {
                                    clipRect(pillLeft, fillTop, pillRight, pillBottom) {
                                        drawSpeakerGlyph(
                                            center = glyphCenter,
                                            glyphSize = glyphSize,
                                            color = paletteSpec.iconOnFill,
                                            volumeFraction = musicVolumeFraction,
                                            pathBuffer = speakerPath,
                                            cutoutColor = paletteSpec.flat
                                        )
                                    }
                                }
                            }
                        } else {
                            val isHudTop = hudScreenEdge == ScreenEdge.TOP
                            val hudBezelY = if (isHudTop) 0f else size.height
                            val inwardDir = if (isHudTop) 1f else -1f
                            val hudLeft = hudStartOffset
                            val notchAlpha = (1f - previewFloatProgress) *
                                if (previewTarget == PreviewTarget.HUD) 1f else 0.85f
                            if (notchAlpha > 0.02f) {
                                val notchH = cutoutThickness * (1f - previewFloatProgress * 0.72f)
                                val notchTop = if (isHudTop) 0f else hudBezelY - notchH
                                translate(left = hudLeft, top = notchTop) {
                                    buildHudHousingPath(
                                        path = hudPath,
                                        width = outerSpan,
                                        height = notchH,
                                        screenEdge = hudScreenEdge,
                                        pillCornerRadiusPx = pillCornerR,
                                        innerPadPx = innerPad,
                                        filletTailPx = filletTail
                                    )
                                    drawPath(path = hudPath, color = Color.Black.copy(alpha = notchAlpha))
                                }
                            }

                            val pillCenterY = hudBezelY + inwardDir * (bezelPad + pillW * 0.5f)
                            val pillTop = pillCenterY - pillW * 0.5f
                            val pillBottom = pillCenterY + pillW * 0.5f
                            val pillLeft = hudLeft + pillStartOffset
                            val pillRight = pillLeft + pillSpan

                            pillPath.reset()
                            pillPath.addRoundRect(
                                RoundRect(
                                    rect = Rect(pillLeft, pillTop, pillRight, pillBottom),
                                    cornerRadius = CornerRadius(pillCornerR, pillCornerR)
                                )
                            )
                            drawPath(path = pillPath, color = Color(0xFF2C2C2E))

                            if (settings.showMenuButton) {
                                drawHudMenuButton(
                                    left = pillRight + HUD_MENU_BUTTON_GAP_DP * scale,
                                    top = pillTop,
                                    sizePx = pillW,
                                    cornerRadiusPx = pillCornerR,
                                    trackColor = Color(0xFF2C2C2E),
                                    dotColor = Color.White,
                                    dotRadiusPx = (pillW * 0.07f).coerceAtLeast(1f)
                                )
                            }

                            val fillRight = (pillLeft + pillSpan * musicVolumeFraction).coerceIn(pillLeft, pillRight)
                            val fillBrush = hudFillBrush(
                                spec = paletteSpec,
                                useGradient = settings.hudUseGradient,
                                start = Offset(pillLeft, pillCenterY),
                                end = Offset(pillRight, pillCenterY),
                                shift = 0f
                            )
                            clipPath(pillPath) {
                                if (!paletteSpec.isClassic) {
                                    drawRect(
                                        brush = fillBrush,
                                        topLeft = Offset(pillLeft, pillTop),
                                        size = Size(pillSpan, pillW),
                                        alpha = 0.26f
                                    )
                                }
                                if (musicVolumeFraction > 0.01f) {
                                    drawRect(
                                        brush = fillBrush,
                                        topLeft = Offset(pillLeft, pillTop),
                                        size = Size(fillRight - pillLeft, pillW)
                                    )
                                }
                                val glyphSize = (pillW * 0.45f).coerceAtLeast(3f)
                                val glyphCenter = Offset(pillLeft + pillW * 0.44f, pillCenterY)
                                if (fillRight > pillLeft) {
                                    clipRect(pillLeft, pillTop, fillRight, pillBottom) {
                                        drawSpeakerGlyph(
                                            center = glyphCenter,
                                            glyphSize = glyphSize,
                                            color = paletteSpec.iconOnFill,
                                            volumeFraction = musicVolumeFraction,
                                            pathBuffer = speakerPath,
                                            cutoutColor = paletteSpec.flat
                                        )
                                    }
                                }
                                if (fillRight < pillRight) {
                                    clipRect(fillRight, pillTop, pillRight, pillBottom) {
                                        drawSpeakerGlyph(
                                            center = glyphCenter,
                                            glyphSize = glyphSize,
                                            color = Color.White,
                                            volumeFraction = musicVolumeFraction,
                                            pathBuffer = speakerPath,
                                            cutoutColor = Color(0xFF2C2C2E)
                                        )
                                    }
                                }
                            }
                        }


                    }

                    val cameraOffset = when {
                        !isLandscape -> Offset(size.width * 0.5f, 9.dp.toPx())
                        displayRotation == Surface.ROTATION_90 -> Offset(9.dp.toPx(), size.height * 0.5f)
                        else -> Offset(size.width - 9.dp.toPx(), size.height * 0.5f)
                    }
                    drawCircle(
                        color = frameStrokeColor.copy(alpha = 0.55f),
                        radius = 3.dp.toPx(),
                        center = cameraOffset
                    )
                }

                drawRoundRect(
                    color = frameStrokeColor,
                    cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                    style = Stroke(width = bezelStrokePx)
                )

            }
        }

        Row(
            modifier = Modifier.padding(top = 4.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PreviewLegendDot(
                color = activeAccent,
                label = when {
                    !settings.enableVolumeSliderHud -> "HUD Off"
                    !hudAllowed -> "System Panel"
                    else -> "Slider HUD"
                },
                onClick = { if (hudAllowed) onPreviewTap(PreviewTarget.HUD) }
            )
        }
    }
}

@Composable
private fun PreviewLegendDot(
    color: Color,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(8.dp),
            shape = CircleShape,
            color = color
        ) {}
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}
