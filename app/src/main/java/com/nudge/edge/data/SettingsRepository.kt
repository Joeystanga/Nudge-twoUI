package com.nudge.edge.data

import com.nudge.edge.model.HudPaletteId
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nudge.edge.model.EdgeSide
import com.nudge.edge.model.NudgeSettings
import com.nudge.edge.model.PreviewTarget
import com.nudge.edge.model.ScreenEdge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private val Context.nudgeDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "nudge_preferences"
)

class SettingsRepository private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private object Keys {
        val ENABLE_VOLUME_SLIDER_HUD = booleanPreferencesKey("enable_volume_slider_hud")
        val SHOW_SYSTEM_PANEL_WHEN_HUD_OFF = booleanPreferencesKey("show_system_panel_when_hud_off")
        val VOLUME_STEP = intPreferencesKey("volume_step")
        val HUD_EDGE_SIDE = stringPreferencesKey("hud_edge_side")
        val HUD_Y = floatPreferencesKey("hud_y")
        val HUD_HEIGHT_DP = floatPreferencesKey("hud_height_dp")
        val HUD_WIDTH_DP = floatPreferencesKey("hud_width_dp")
        val HUD_CORNER_RADIUS_DP = floatPreferencesKey("hud_corner_radius_dp")
        val HUD_AUTO_HIDE_MS = longPreferencesKey("hud_auto_hide_ms")
        val ENABLE_SLIDER_HUD_LANDSCAPE = booleanPreferencesKey("enable_slider_hud_landscape")
        val LANDSCAPE_HUD_EDGE_SIDE = stringPreferencesKey("landscape_hud_edge_side")
        val LANDSCAPE_HUD_Y = floatPreferencesKey("landscape_hud_y")
        val SHOW_SYSTEM_PANEL_LANDSCAPE = booleanPreferencesKey("show_system_panel_landscape")
        val FLOATING_MODE = booleanPreferencesKey("floating_mode")
        val SHOW_MENU_BUTTON = booleanPreferencesKey("show_menu_button")
        val HUD_PALETTE = stringPreferencesKey("hud_palette")
        val HUD_USE_GRADIENT = booleanPreferencesKey("hud_use_gradient")
        val HUD_ANIMATE_GRADIENT = booleanPreferencesKey("hud_animate_gradient")
        val HUD_EDGE_INSET_DP = floatPreferencesKey("hud_edge_inset_dp")
        val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
        val LIGHT_MODE = booleanPreferencesKey("light_mode")
        val LAST_PROMPTED_ACCESSIBILITY_VERSION = intPreferencesKey("last_prompted_accessibility_version")
    }

    private val _settings = MutableStateFlow(NudgeSettings())
    val settings: StateFlow<NudgeSettings> = _settings.asStateFlow()

    private val _previewTarget = MutableStateFlow(PreviewTarget.NONE)
    val previewTarget: StateFlow<PreviewTarget> = _previewTarget.asStateFlow()

    private val _preferencesLoaded = MutableStateFlow(false)
    val preferencesLoaded: StateFlow<Boolean> = _preferencesLoaded.asStateFlow()

    private val _lastPromptedAccessibilityVersion = MutableStateFlow(0)
    val lastPromptedAccessibilityVersion: StateFlow<Int> = _lastPromptedAccessibilityVersion.asStateFlow()

    private var previewClearJob: Job? = null

    init {
        scope.launch {
            appContext.nudgeDataStore.data
                .distinctUntilChanged()
                .collectLatest { prefs ->
                    _settings.value = mapPreferences(prefs)
                    _lastPromptedAccessibilityVersion.value =
                        prefs[Keys.LAST_PROMPTED_ACCESSIBILITY_VERSION] ?: 0
                    _preferencesLoaded.value = true
                }
        }
    }

    private fun mapPreferences(prefs: Preferences): NudgeSettings {
        val defaults = NudgeSettings()
        return NudgeSettings(
            enableVolumeSliderHud =
                prefs[Keys.ENABLE_VOLUME_SLIDER_HUD] ?: defaults.enableVolumeSliderHud,
            showSystemPanelWhenHudOff =
                prefs[Keys.SHOW_SYSTEM_PANEL_WHEN_HUD_OFF] ?: defaults.showSystemPanelWhenHudOff,
            volumeStep = NudgeSettings.clampVolumeStep(
                prefs[Keys.VOLUME_STEP] ?: defaults.volumeStep
            ),
            hudEdgeSide = prefs[Keys.HUD_EDGE_SIDE]?.let {
                runCatching { EdgeSide.valueOf(it) }.getOrNull()
            } ?: defaults.hudEdgeSide,
            hudYFraction = NudgeSettings.clampFraction(
                prefs[Keys.HUD_Y] ?: defaults.hudYFraction
            ),
            hudHeightDp = NudgeSettings.clampHudHeight(
                prefs[Keys.HUD_HEIGHT_DP] ?: defaults.hudHeightDp
            ),
            hudWidthDp = NudgeSettings.clampHudWidth(
                prefs[Keys.HUD_WIDTH_DP] ?: defaults.hudWidthDp
            ),
            hudCornerRadiusDp = NudgeSettings.clampHudCornerRadius(
                prefs[Keys.HUD_CORNER_RADIUS_DP] ?: defaults.hudCornerRadiusDp
            ),
            hudAutoHideMs = NudgeSettings.clampAutoHideMs(
                prefs[Keys.HUD_AUTO_HIDE_MS] ?: defaults.hudAutoHideMs
            ),
            enableSliderHudInLandscape =
                prefs[Keys.ENABLE_SLIDER_HUD_LANDSCAPE] ?: defaults.enableSliderHudInLandscape,
            landscapeHudEdgeSide = prefs[Keys.LANDSCAPE_HUD_EDGE_SIDE]?.let {
                runCatching { ScreenEdge.valueOf(it) }.getOrNull()
            } ?: defaults.landscapeHudEdgeSide,
            landscapeHudYFraction = NudgeSettings.clampFraction(
                prefs[Keys.LANDSCAPE_HUD_Y] ?: defaults.landscapeHudYFraction
            ),
            showSystemPanelInLandscape =
                prefs[Keys.SHOW_SYSTEM_PANEL_LANDSCAPE] ?: defaults.showSystemPanelInLandscape,
            floatingMode = prefs[Keys.FLOATING_MODE] ?: defaults.floatingMode,
            showMenuButton = prefs[Keys.SHOW_MENU_BUTTON] ?: defaults.showMenuButton,
            hudPalette = prefs[Keys.HUD_PALETTE]?.let {
                runCatching { HudPaletteId.valueOf(it) }.getOrNull()
            } ?: defaults.hudPalette,
            hudUseGradient = prefs[Keys.HUD_USE_GRADIENT] ?: defaults.hudUseGradient,
            hudAnimateGradient = prefs[Keys.HUD_ANIMATE_GRADIENT] ?: defaults.hudAnimateGradient,
            hudEdgeInsetDp = NudgeSettings.clampHudEdgeInset(
                prefs[Keys.HUD_EDGE_INSET_DP] ?: defaults.hudEdgeInsetDp
            ),
            hapticsEnabled = prefs[Keys.HAPTICS_ENABLED] ?: defaults.hapticsEnabled,
            lightMode = prefs[Keys.LIGHT_MODE] ?: defaults.lightMode
        )
    }

    fun requestLivePreview(target: PreviewTarget, durationMs: Long = 1800L) {
        _previewTarget.value = target
        previewClearJob?.cancel()
        if (target != PreviewTarget.NONE) {
            previewClearJob = scope.launch {
                delay(durationMs)
                _previewTarget.value = PreviewTarget.NONE
            }
        }
    }

    fun clearLivePreview() {
        previewClearJob?.cancel()
        _previewTarget.value = PreviewTarget.NONE
    }

    fun setEnableVolumeSliderHud(enabled: Boolean) {
        if (_settings.value.enableVolumeSliderHud == enabled) return
        _settings.value = _settings.value.copy(enableVolumeSliderHud = enabled)
        if (enabled) {
            requestLivePreview(PreviewTarget.HUD)
        } else {
            clearLivePreview()
        }
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.ENABLE_VOLUME_SLIDER_HUD] = enabled }
        }
    }

    fun setShowSystemPanelWhenHudOff(enabled: Boolean) {
        if (_settings.value.showSystemPanelWhenHudOff == enabled) return
        _settings.value = _settings.value.copy(showSystemPanelWhenHudOff = enabled)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.SHOW_SYSTEM_PANEL_WHEN_HUD_OFF] = enabled }
        }
    }

    fun setVolumeStep(step: Int) {
        val clamped = NudgeSettings.clampVolumeStep(step)
        if (_settings.value.volumeStep == clamped) return
        _settings.value = _settings.value.copy(volumeStep = clamped)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.VOLUME_STEP] = clamped }
        }
    }

    fun setHudEdgeSide(side: EdgeSide) {
        if (_settings.value.hudEdgeSide == side) return
        _settings.value = _settings.value.copy(hudEdgeSide = side)
        requestLivePreview(PreviewTarget.HUD)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.HUD_EDGE_SIDE] = side.name }
        }
    }

    fun setHudYFraction(fraction: Float) {
        val clamped = NudgeSettings.clampFraction(fraction)
        if (_settings.value.hudYFraction == clamped) return
        _settings.value = _settings.value.copy(hudYFraction = clamped)
        requestLivePreview(PreviewTarget.HUD)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.HUD_Y] = clamped }
        }
    }

    fun setHudHeightDp(heightDp: Float) {
        val clamped = NudgeSettings.clampHudHeight(heightDp)
        if (_settings.value.hudHeightDp == clamped) return
        _settings.value = _settings.value.copy(hudHeightDp = clamped)
        requestLivePreview(PreviewTarget.HUD)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.HUD_HEIGHT_DP] = clamped }
        }
    }

    fun setHudWidthDp(widthDp: Float) {
        val clamped = NudgeSettings.clampHudWidth(widthDp)
        if (_settings.value.hudWidthDp == clamped) return
        _settings.value = _settings.value.copy(hudWidthDp = clamped)
        requestLivePreview(PreviewTarget.HUD)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.HUD_WIDTH_DP] = clamped }
        }
    }

    fun setHudCornerRadiusDp(cornerRadiusDp: Float) {
        val clamped = NudgeSettings.clampHudCornerRadius(cornerRadiusDp)
        if (_settings.value.hudCornerRadiusDp == clamped) return
        _settings.value = _settings.value.copy(hudCornerRadiusDp = clamped)
        requestLivePreview(PreviewTarget.HUD)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.HUD_CORNER_RADIUS_DP] = clamped }
        }
    }

    fun setHudAutoHideMs(timeoutMs: Long) {
        val clamped = NudgeSettings.clampAutoHideMs(timeoutMs)
        if (_settings.value.hudAutoHideMs == clamped) return
        _settings.value = _settings.value.copy(hudAutoHideMs = clamped)
        requestLivePreview(PreviewTarget.HUD)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.HUD_AUTO_HIDE_MS] = clamped }
        }
    }

    fun setEnableSliderHudInLandscape(enabled: Boolean) {
        if (_settings.value.enableSliderHudInLandscape == enabled) return
        _settings.value = _settings.value.copy(enableSliderHudInLandscape = enabled)
        if (enabled) {
            requestLivePreview(PreviewTarget.HUD)
        } else {
            clearLivePreview()
        }
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.ENABLE_SLIDER_HUD_LANDSCAPE] = enabled }
        }
    }

    fun setLandscapeHudEdgeSide(side: ScreenEdge) {
        if (_settings.value.landscapeHudEdgeSide == side) return
        _settings.value = _settings.value.copy(landscapeHudEdgeSide = side)
        requestLivePreview(PreviewTarget.HUD)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.LANDSCAPE_HUD_EDGE_SIDE] = side.name }
        }
    }

    fun setLandscapeHudYFraction(fraction: Float) {
        val clamped = NudgeSettings.clampFraction(fraction)
        if (_settings.value.landscapeHudYFraction == clamped) return
        _settings.value = _settings.value.copy(landscapeHudYFraction = clamped)
        requestLivePreview(PreviewTarget.HUD)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.LANDSCAPE_HUD_Y] = clamped }
        }
    }

    fun setShowSystemPanelInLandscape(enabled: Boolean) {
        if (_settings.value.showSystemPanelInLandscape == enabled) return
        _settings.value = _settings.value.copy(showSystemPanelInLandscape = enabled)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.SHOW_SYSTEM_PANEL_LANDSCAPE] = enabled }
        }
    }

    fun setFloatingMode(enabled: Boolean) {
        if (_settings.value.floatingMode == enabled) return
        _settings.value = _settings.value.copy(floatingMode = enabled)
        requestLivePreview(PreviewTarget.HUD, durationMs = 2400L)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.FLOATING_MODE] = enabled }
        }
    }

    fun setShowMenuButton(show: Boolean) {
        if (_settings.value.showMenuButton == show) return
        _settings.value = _settings.value.copy(showMenuButton = show)
        requestLivePreview(PreviewTarget.HUD, durationMs = 2400L)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.SHOW_MENU_BUTTON] = show }
        }
    }

    fun setHudPalette(palette: HudPaletteId) {
        if (_settings.value.hudPalette == palette) return
        _settings.value = _settings.value.copy(hudPalette = palette)
        requestLivePreview(PreviewTarget.HUD, durationMs = 2400L)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.HUD_PALETTE] = palette.name }
        }
    }

    fun setHudUseGradient(enabled: Boolean) {
        if (_settings.value.hudUseGradient == enabled) return
        _settings.value = _settings.value.copy(hudUseGradient = enabled)
        requestLivePreview(PreviewTarget.HUD, durationMs = 2400L)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.HUD_USE_GRADIENT] = enabled }
        }
    }

    fun setHudAnimateGradient(enabled: Boolean) {
        if (_settings.value.hudAnimateGradient == enabled) return
        _settings.value = _settings.value.copy(hudAnimateGradient = enabled)
        requestLivePreview(PreviewTarget.HUD, durationMs = 2400L)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.HUD_ANIMATE_GRADIENT] = enabled }
        }
    }

    fun setHudEdgeInsetDp(insetDp: Float) {
        val clamped = NudgeSettings.clampHudEdgeInset(insetDp)
        if (_settings.value.hudEdgeInsetDp == clamped) return
        _settings.value = _settings.value.copy(hudEdgeInsetDp = clamped)
        requestLivePreview(PreviewTarget.HUD)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.HUD_EDGE_INSET_DP] = clamped }
        }
    }

    fun setHapticsEnabled(enabled: Boolean) {
        if (_settings.value.hapticsEnabled == enabled) return
        _settings.value = _settings.value.copy(hapticsEnabled = enabled)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.HAPTICS_ENABLED] = enabled }
        }
    }

    fun setLightMode(enabled: Boolean) {
        if (_settings.value.lightMode == enabled) return
        _settings.value = _settings.value.copy(lightMode = enabled)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.LIGHT_MODE] = enabled }
        }
    }

    fun markAccessibilityPromptHandled(versionCode: Int) {
        if (_lastPromptedAccessibilityVersion.value >= versionCode) return
        _lastPromptedAccessibilityVersion.value = versionCode
        scope.launch {
            appContext.nudgeDataStore.edit {
                it[Keys.LAST_PROMPTED_ACCESSIBILITY_VERSION] = versionCode
            }
        }
    }

    companion object {
        @Volatile
        private var instance: SettingsRepository? = null

        fun getInstance(context: Context): SettingsRepository {
            return instance ?: synchronized(this) {
                instance ?: SettingsRepository(context).also { instance = it }
            }
        }
    }
}
