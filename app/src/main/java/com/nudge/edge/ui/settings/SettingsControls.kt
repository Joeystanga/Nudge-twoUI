package com.nudge.edge.ui.settings

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.layout.Box
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nudge.edge.R
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nudge.edge.data.SettingsRepository
import com.nudge.edge.model.EdgeSide
import com.nudge.edge.model.NudgeSettings
import com.nudge.edge.model.ScreenEdge
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SettingsPage(
    settings: NudgeSettings,
    portraitAxisDp: Float,
    landscapeAxisDp: Float,
    musicMaxVolume: Int,
    serviceActive: Boolean,
    repository: SettingsRepository,
    onOpenAccessibilitySettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionHeader("Design")
        DesignSection(
            settings = settings,
            portraitAxisDp = portraitAxisDp,
            landscapeAxisDp = landscapeAxisDp,
            repository = repository
        )

        SectionHeader("Theme")
        ThemeSection(settings = settings, repository = repository)

        SectionHeader("Behavior")
        BehaviorSection(
            settings = settings,
            musicMaxVolume = musicMaxVolume,
            repository = repository
        )

        SectionHeader("App")
        AppSection(
            serviceActive = serviceActive,
            onOpenAccessibilitySettings = onOpenAccessibilitySettings
        )

        Spacer(modifier = Modifier.navigationBarsPadding())
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 8.dp, top = 12.dp)
    )
}

@Composable
private fun GroupedCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(content = content)
    }
}

@Composable
private fun GroupDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

@Composable
private fun DesignSection(
    settings: NudgeSettings,
    portraitAxisDp: Float,
    landscapeAxisDp: Float,
    repository: SettingsRepository
) {
    GroupedCard {
        AnimatedVisibility(
            visible = settings.enableVolumeSliderHud,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column {
                Surface(
                    shape = RectangleShape,
                    color = Color.Transparent,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column {
                            Text(
                                text = "HUD Edge Anchor",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Screen edge for the volume slider",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        EdgeSideSegmentedButtons(
                            selectedSide = settings.hudEdgeSide,
                            onSideSelected = repository::setHudEdgeSide
                        )
                    }
                }

                GroupDivider()

                Surface(
                    shape = RectangleShape,
                    color = Color.Transparent,
                    onClick = { repository.setFloatingMode(!settings.floatingMode) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Floating Mode",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (settings.floatingMode) {
                                    "Now: Standalone floating pill"
                                } else {
                                    "Now: Edge-merged bezel notch"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = settings.floatingMode,
                            onCheckedChange = repository::setFloatingMode
                        )
                    }
                }

                GroupDivider()

                StepperSliderCard(
                    title = "Edge Inset",
                    valueLabel = "${settings.hudEdgeInsetDp.roundToInt()} dp",
                    value = settings.hudEdgeInsetDp,
                    valueRange = NudgeSettings.MIN_HUD_EDGE_INSET_DP..NudgeSettings.MAX_HUD_EDGE_INSET_DP,
                    onValueChange = { repository.setHudEdgeInsetDp(it.roundToInt().toFloat()) },
                    onStepDown = { repository.setHudEdgeInsetDp(settings.hudEdgeInsetDp - 1f) },
                    onStepUp = { repository.setHudEdgeInsetDp(settings.hudEdgeInsetDp + 1f) },
                    stepDescription = "1 dp step"
                )

                GroupDivider()

                StepperSliderCard(
                    title = "HUD Y-Axis Position",
                    valueLabel = formatYAxisLabel(settings.hudYFraction, portraitAxisDp),
                    value = settings.hudYFraction,
                    valueRange = 0f..1f,
                    onValueChange = repository::setHudYFraction,
                    onStepDown = {
                        repository.setHudYFraction(
                            NudgeSettings.stepFractionByDp(
                                settings.hudYFraction,
                                -1f,
                                portraitAxisDp
                            )
                        )
                    },
                    onStepUp = {
                        repository.setHudYFraction(
                            NudgeSettings.stepFractionByDp(
                                settings.hudYFraction,
                                1f,
                                portraitAxisDp
                            )
                        )
                    },
                    stepDescription = "1 dp step"
                )

                GroupDivider()

                StepperSliderCard(
                    title = "HUD Pill Height",
                    valueLabel = "${settings.hudHeightDp.roundToInt()} dp",
                    value = settings.hudHeightDp,
                    valueRange = NudgeSettings.MIN_HUD_HEIGHT_DP..NudgeSettings.MAX_HUD_HEIGHT_DP,
                    onValueChange = { repository.setHudHeightDp(it.roundToInt().toFloat()) },
                    onStepDown = { repository.setHudHeightDp(settings.hudHeightDp - 1f) },
                    onStepUp = { repository.setHudHeightDp(settings.hudHeightDp + 1f) },
                    stepDescription = "1 dp step"
                )

                GroupDivider()

                StepperSliderCard(
                    title = "HUD Pill Width",
                    valueLabel = "${settings.hudWidthDp.roundToInt()} dp",
                    value = settings.hudWidthDp,
                    valueRange = NudgeSettings.MIN_HUD_WIDTH_DP..NudgeSettings.MAX_HUD_WIDTH_DP,
                    onValueChange = { repository.setHudWidthDp(it.roundToInt().toFloat()) },
                    onStepDown = { repository.setHudWidthDp(settings.hudWidthDp - 1f) },
                    onStepUp = { repository.setHudWidthDp(settings.hudWidthDp + 1f) },
                    stepDescription = "1 dp fixed width"
                )

                GroupDivider()

                val maxCornerDp = (settings.hudWidthDp * 0.5f).coerceAtLeast(1f)
                val effectiveCornerDp = settings.hudCornerRadiusDp.coerceIn(
                    NudgeSettings.MIN_HUD_CORNER_RADIUS_DP,
                    maxCornerDp
                )
                StepperSliderCard(
                    title = "HUD Corner Radius",
                    valueLabel = "${effectiveCornerDp.roundToInt()} dp",
                    value = effectiveCornerDp,
                    valueRange = NudgeSettings.MIN_HUD_CORNER_RADIUS_DP..maxCornerDp,
                    onValueChange = { raw ->
                        val snapped = if (raw >= maxCornerDp - 0.35f) {
                            maxCornerDp
                        } else {
                            raw.roundToInt().toFloat().coerceIn(
                                NudgeSettings.MIN_HUD_CORNER_RADIUS_DP,
                                maxCornerDp
                            )
                        }
                        repository.setHudCornerRadiusDp(snapped)
                    },
                    onStepDown = {
                        repository.setHudCornerRadiusDp(
                            (effectiveCornerDp - 1f).coerceAtLeast(NudgeSettings.MIN_HUD_CORNER_RADIUS_DP)
                        )
                    },
                    onStepUp = {
                        repository.setHudCornerRadiusDp(minOf(effectiveCornerDp + 1f, maxCornerDp))
                    },
                    stepDescription = "0 to ${maxCornerDp.roundToInt()} dp (1 dp step)"
                )

                GroupDivider()
            }
        }

        Surface(
            shape = RectangleShape,
            color = Color.Transparent,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ScreenRotation,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Landscape Volume Options",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (settings.enableVolumeSliderHud) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        onClick = {
                            repository.setEnableSliderHudInLandscape(!settings.enableSliderHudInLandscape)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Show Custom Slider",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Switch(
                                checked = settings.enableSliderHudInLandscape,
                                onCheckedChange = repository::setEnableSliderHudInLandscape
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = settings.enableSliderHudInLandscape,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        val landscapeIsVerticalEdge =
                            settings.landscapeHudEdgeSide == ScreenEdge.LEFT ||
                                settings.landscapeHudEdgeSide == ScreenEdge.RIGHT
                        val landscapePositionAxisDp = if (landscapeIsVerticalEdge) {
                            landscapeAxisDp
                        } else {
                            portraitAxisDp
                        }
                        val landscapePositionTitle = if (landscapeIsVerticalEdge) {
                            "Vertical Position"
                        } else {
                            "Horizontal Position"
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Slider Edge",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                    ScreenEdgeSegmentedButtons(
                                        selectedEdge = settings.landscapeHudEdgeSide,
                                        onEdgeSelected = repository::setLandscapeHudEdgeSide
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    InlineStepperSlider(
                                        title = landscapePositionTitle,
                                        valueLabel = formatYAxisLabel(
                                            settings.landscapeHudYFraction,
                                            landscapePositionAxisDp
                                        ),
                                        value = settings.landscapeHudYFraction,
                                        valueRange = 0f..1f,
                                        onValueChange = repository::setLandscapeHudYFraction,
                                        onStepDown = {
                                            repository.setLandscapeHudYFraction(
                                                NudgeSettings.stepFractionByDp(
                                                    settings.landscapeHudYFraction,
                                                    -1f,
                                                    landscapePositionAxisDp
                                                )
                                            )
                                        },
                                        onStepUp = {
                                            repository.setLandscapeHudYFraction(
                                                NudgeSettings.stepFractionByDp(
                                                    settings.landscapeHudYFraction,
                                                    1f,
                                                    landscapePositionAxisDp
                                                )
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                AnimatedVisibility(
                    visible = !settings.enableVolumeSliderHud || !settings.enableSliderHudInLandscape,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        onClick = {
                            repository.setShowSystemPanelInLandscape(!settings.showSystemPanelInLandscape)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Show System Volume Slider",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Switch(
                                checked = settings.showSystemPanelInLandscape,
                                onCheckedChange = repository::setShowSystemPanelInLandscape
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeSection(
    settings: NudgeSettings,
    repository: SettingsRepository
) {
    GroupedCard {
        Surface(
            shape = RectangleShape,
            color = Color.Transparent,
            onClick = { repository.setLightMode(!settings.lightMode) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LightMode,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = "Light Mode",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Light theme for app & mixer",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = settings.lightMode,
                    onCheckedChange = repository::setLightMode
                )
            }
        }
    }
}

@Composable
private fun BehaviorSection(
    settings: NudgeSettings,
    musicMaxVolume: Int,
    repository: SettingsRepository
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
            onClick = { repository.setEnableVolumeSliderHud(!settings.enableVolumeSliderHud) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Enable Volume Slider HUD",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = settings.enableVolumeSliderHud,
                    onCheckedChange = repository::setEnableVolumeSliderHud
                )
            }
        }

        GroupedCard {
            AnimatedVisibility(
                visible = !settings.enableVolumeSliderHud,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    Surface(
                        shape = RectangleShape,
                        color = Color.Transparent,
                        onClick = {
                            repository.setShowSystemPanelWhenHudOff(!settings.showSystemPanelWhenHudOff)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Show Standard System Volume Slider",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Switch(
                                checked = settings.showSystemPanelWhenHudOff,
                                onCheckedChange = repository::setShowSystemPanelWhenHudOff
                            )
                        }
                    }
                    GroupDivider()
                }
            }

            AnimatedVisibility(
                visible = settings.enableVolumeSliderHud,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    StepperSliderCard(
                        title = "Auto-Hide Timeout",
                        valueLabel = formatTimeoutLabel(settings.hudAutoHideMs),
                        value = settings.hudAutoHideMs.toFloat(),
                        valueRange = NudgeSettings.MIN_AUTO_HIDE_MS.toFloat()..NudgeSettings.MAX_AUTO_HIDE_MS.toFloat(),
                        onValueChange = { raw ->
                            val snapped = ((raw / NudgeSettings.AUTO_HIDE_STEP_MS.toFloat()).roundToInt() *
                                NudgeSettings.AUTO_HIDE_STEP_MS)
                            repository.setHudAutoHideMs(snapped)
                        },
                        onStepDown = {
                            repository.setHudAutoHideMs(
                                settings.hudAutoHideMs - NudgeSettings.AUTO_HIDE_STEP_MS
                            )
                        },
                        onStepUp = {
                            repository.setHudAutoHideMs(
                                settings.hudAutoHideMs + NudgeSettings.AUTO_HIDE_STEP_MS
                            )
                        },
                        stepDescription = "50 ms step"
                    )
                    GroupDivider()
                }
            }

            StepperSliderCard(
                title = "Volume Step",
                valueLabel = "${settings.volumeStep} ${if (settings.volumeStep == 1) "step" else "steps"}",
                value = settings.volumeStep.toFloat(),
                valueRange = NudgeSettings.MIN_VOLUME_STEP.toFloat()..NudgeSettings.MAX_VOLUME_STEP.toFloat(),
                onValueChange = { repository.setVolumeStep(it.roundToInt()) },
                onStepDown = { repository.setVolumeStep(settings.volumeStep - 1) },
                onStepUp = { repository.setVolumeStep(settings.volumeStep + 1) },
                stepDescription = null
            )

            GroupDivider()

            Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Detected Device Scale: 0 \u2013 $musicMaxVolume steps",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            GroupDivider()

            Surface(
                shape = RectangleShape,
                color = Color.Transparent,
                onClick = { repository.setHapticsEnabled(!settings.hapticsEnabled) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Vibration,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = "Haptics on Button Press",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Haptic click on keys & gestures",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = settings.hapticsEnabled,
                        onCheckedChange = repository::setHapticsEnabled
                    )
                }
            }
        }
    }
}

@Composable
private fun AppSection(
    serviceActive: Boolean,
    onOpenAccessibilitySettings: () -> Unit
) {
    val context = LocalContext.current
    val versionName = remember {
        runCatching {
            context.packageManager
                .getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0L))
                .versionName
        }.getOrNull() ?: "unknown"
    }

    GroupedCard {
        Surface(
            shape = RectangleShape,
            color = Color.Transparent,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (serviceActive) {
                            MaterialTheme.colorScheme.tertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        },
                        modifier = Modifier.size(42.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AccessibilityNew,
                            contentDescription = null,
                            tint = if (serviceActive) {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            },
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (serviceActive) {
                                "Accessibility Service Active"
                            } else {
                                "Enable Accessibility Service"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (serviceActive) {
                                "Hardware keys intercepted globally"
                            } else {
                                "Required for key & overlay access"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Button(
                    onClick = onOpenAccessibilitySettings,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(vertical = 14.dp)
                ) {
                    Text(
                        text = if (serviceActive) {
                            "Manage Accessibility Settings"
                        } else {
                            "Open Accessibility Settings"
                        },
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        GroupDivider()

        Surface(
            shape = RectangleShape,
            color = Color.Transparent,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Version $versionName",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Based on Nudge by DarkAspect (MIT License)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EdgeSideSegmentedButtons(
    selectedSide: EdgeSide,
    onSideSelected: (EdgeSide) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        EdgeSide.entries.forEach { side ->
            SelectablePillButton(
                label = if (side == EdgeSide.LEFT) "Left Edge" else "Right Edge",
                selected = selectedSide == side,
                onClick = { onSideSelected(side) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ScreenEdgeSegmentedButtons(
    selectedEdge: ScreenEdge,
    onEdgeSelected: (ScreenEdge) -> Unit
) {
    val rows = listOf(
        listOf(ScreenEdge.LEFT to "Left", ScreenEdge.RIGHT to "Right"),
        listOf(ScreenEdge.TOP to "Top", ScreenEdge.BOTTOM to "Bottom")
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { rowEdges ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowEdges.forEach { (edge, label) ->
                    SelectablePillButton(
                        label = label,
                        selected = selectedEdge == edge,
                        onClick = { onEdgeSelected(edge) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectablePillButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        modifier = modifier
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp)
        )
    }
}

@Composable
private fun InlineStepperSlider(
    title: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onStepDown: () -> Unit,
    onStepUp: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            )
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    text = valueLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalIconButton(
                onClick = onStepDown,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Remove,
                    contentDescription = "Decrease $title",
                    modifier = Modifier.size(16.dp)
                )
            }
            Slider(
                value = value.coerceIn(valueRange.start, valueRange.endInclusive),
                onValueChange = onValueChange,
                valueRange = valueRange,
                modifier = Modifier.weight(1f)
            )
            FilledTonalIconButton(
                onClick = onStepUp,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "Increase $title",
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun StepperSliderCard(
    title: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onStepDown: () -> Unit,
    onStepUp: () -> Unit,
    stepDescription: String? = null
) {
    Surface(
        shape = RectangleShape,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!stepDescription.isNullOrEmpty()) {
                        Text(
                            text = stepDescription,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = valueLabel,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalIconButton(
                    onClick = onStepDown,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Remove,
                        contentDescription = "Decrease $title",
                        modifier = Modifier.size(18.dp)
                    )
                }

                Slider(
                    value = value.coerceIn(valueRange.start, valueRange.endInclusive),
                    onValueChange = onValueChange,
                    valueRange = valueRange,
                    modifier = Modifier.weight(1f)
                )

                FilledTonalIconButton(
                    onClick = onStepUp,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "Increase $title",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

private fun formatYAxisLabel(fraction: Float, axisDp: Float): String {
    val percent = (fraction * 100f).roundToInt()
    val dpPos = (fraction * axisDp).roundToInt()
    return "$percent% · $dpPos dp"
}

private fun formatTimeoutLabel(timeoutMs: Long): String {
    return if (timeoutMs < 1000L) {
        "$timeoutMs ms"
    } else {
        String.format(Locale.US, "%.2f s", timeoutMs / 1000f)
    }
}
