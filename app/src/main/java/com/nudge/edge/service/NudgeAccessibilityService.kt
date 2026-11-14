package com.nudge.edge.service

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.hardware.camera2.CameraManager
import android.hardware.display.DisplayManager
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.view.Display
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Surface
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.nudge.edge.MainActivity
import com.nudge.edge.data.SettingsRepository
import com.nudge.edge.data.VolumeController
import com.nudge.edge.model.EdgeOrientationMapper
import com.nudge.edge.model.NudgeSettings
import com.nudge.edge.model.PreviewTarget
import com.nudge.edge.model.ScreenEdge
import com.nudge.edge.ui.components.QuickVolumeModal
import com.nudge.edge.ui.components.VolumeSliderHud
import com.nudge.edge.ui.components.computeHudCutoutThicknessDp
import com.nudge.edge.ui.components.computeHudOuterSpanDp
import com.nudge.edge.ui.theme.NudgeTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.ceil

class NudgeAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var windowManager: WindowManager
    private lateinit var displayManager: DisplayManager
    private lateinit var powerManager: PowerManager
    private lateinit var cameraManager: CameraManager
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var volumeController: VolumeController
    private val lifecycleOwner = OverlayLifecycleOwner()

    private var audioThread: HandlerThread? = null
    private var audioHandler: Handler? = null
    private var audioScope: CoroutineScope? = null

    private var hudView: ComposeView? = null
    private var modalView: View? = null

    private var hudParams: WindowManager.LayoutParams? = null
    private var modalParams: WindowManager.LayoutParams? = null

    private val hudActive = MutableStateFlow(false)
    private val modalActive = MutableStateFlow(false)
    private val volumeFraction = MutableStateFlow(0.5f)
    private val screenSizeState = MutableStateFlow(Pair(1080, 2400))
    private val rotationState = MutableStateFlow(Surface.ROTATION_0)
    private val rotationResetEpoch = MutableStateFlow(0)

    private var upRepeatJob: Job? = null
    private var downRepeatJob: Job? = null
    private var hudHideJob: Job? = null
    private var hudCollapseGoneJob: Job? = null
    private var modalCollapseGoneJob: Job? = null
    private var hudScrubbing = false

    @Volatile
    private var lastProgrammaticAudioTimeMs = 0L

    @Volatile
    private var modalOpenedTimeMs = 0L

    @Volatile
    private var isScreenInteractive = true

    @Volatile
    private var currentForegroundPackage = ""

    @Volatile
    private var cameraPackages: Set<String> = emptySet()

    @Volatile
    private var publicCameraIds: Set<String> = emptySet()

    private val activeCameraIds = ConcurrentHashMap.newKeySet<String>()
    private val torchActiveCameraIds = ConcurrentHashMap.newKeySet<String>()
    private val holdSequence = AtomicLong(0L)

    @Volatile
    private var keyHeldDown = false
    private var consumedUpKeyDown = false
    private var consumedDownKeyDown = false
    private var consumedBackKeyDown = false

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (!publicCameraIds.contains(cameraId)) return
            if (enabled) {
                torchActiveCameraIds.add(cameraId)
            } else {
                torchActiveCameraIds.remove(cameraId)
            }
        }

        override fun onTorchModeUnavailable(cameraId: String) {
            torchActiveCameraIds.remove(cameraId)
        }
    }

    private val cameraAvailabilityCallback = object : CameraManager.AvailabilityCallback() {
        override fun onCameraAvailable(cameraId: String) {
            activeCameraIds.remove(cameraId)
        }

        override fun onCameraUnavailable(cameraId: String) {
            if (!publicCameraIds.contains(cameraId)) return
            activeCameraIds.add(cameraId)
        }
    }

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {
            if (displayId == Display.DEFAULT_DISPLAY) {
                handleDisplayChange()
            }
        }

        override fun onDisplayRemoved(displayId: Int) = Unit

        override fun onDisplayChanged(displayId: Int) {
            if (displayId == Display.DEFAULT_DISPLAY) {
                handleDisplayChange()
            }
        }
    }

    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != "android.media.VOLUME_CHANGED_ACTION") return
            val streamType = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1)
            if (streamType < 0) return
            val osNewValue = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_VALUE", -1)

            audioScope?.launch {
                if (osNewValue > 0) {
                    volumeController.recordObservedStreamValue(streamType, osNewValue)
                }
                val now = SystemClock.uptimeMillis()
                if (streamType == AudioManager.STREAM_MUSIC &&
                    !hudScrubbing &&
                    !keyHeldDown &&
                    (now - lastProgrammaticAudioTimeMs) > 250L
                ) {
                    volumeFraction.value = volumeController.getMusicVolumeFraction()
                }
            }
        }
    }

    private val powerScreenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            if (action == Intent.ACTION_SCREEN_OFF) {
                isScreenInteractive = false
                holdSequence.incrementAndGet()
                closeMixerAndVolumeOverlaysOnScreenOff()
            } else if (action == Intent.ACTION_SCREEN_ON) {
                isScreenInteractive = true
            } else if (action == Intent.ACTION_USER_PRESENT) {
                isScreenInteractive = true
                return
            }
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onServiceConnected() {
        super.onServiceConnected()
        _isServiceRunning.value = true

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        settingsRepository = SettingsRepository.getInstance(this)
        volumeController = VolumeController(this)
        isScreenInteractive = runCatching { powerManager.isInteractive }.getOrDefault(true)

        val audioBgHandler = ensureAudioExecutor()
        audioScope?.launch {
            registerCameraAndAudioCallbacks(audioBgHandler)
            volumeFraction.value = volumeController.getMusicVolumeFraction()
        }

        val initialDisplay = readDisplayRotationAndSize()
        rotationState.value = initialDisplay.first
        screenSizeState.value = initialDisplay.second

        displayManager.registerDisplayListener(
            displayListener,
            Handler(Looper.getMainLooper())
        )

        lifecycleOwner.performCreate()
        setupOverlayWindows()
        lifecycleOwner.onAttached()

        val volumeFilter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")
        val powerFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(volumeReceiver, volumeFilter, Context.RECEIVER_NOT_EXPORTED)
            registerReceiver(powerScreenReceiver, powerFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(volumeReceiver, volumeFilter)
            registerReceiver(powerScreenReceiver, powerFilter)
        }

        observeOverlayLayout()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString()
            val className = event.className?.toString()
            if (!pkg.isNullOrBlank() && pkg != packageName) {
                if (className != "android.widget.Toast") {
                    currentForegroundPackage = pkg
                }
                isScreenInteractive = true
                if (modalActive.value &&
                    className != "android.widget.Toast" &&
                    (SystemClock.uptimeMillis() - modalOpenedTimeMs) > 150L
                ) {
                    dismissModalOverlay()
                }
            }
        }
    }

    override fun onInterrupt() {
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        handleDisplayChange()
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event == null) return super.onKeyEvent(event)
        if (!::settingsRepository.isInitialized || !::volumeController.isInitialized) {
            return super.onKeyEvent(event)
        }
        val keyCode = event.keyCode
        val currentSettings = settingsRepository.settings.value
        val isLandscape = EdgeOrientationMapper.isLandscapeRotation(rotationState.value)

        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    if (event.repeatCount == 0 && modalActive.value) {
                        consumedBackKeyDown = true
                        dismissModalOverlay()
                        return true
                    }
                    if (event.repeatCount > 0 && consumedBackKeyDown) {
                        return true
                    }
                }
                KeyEvent.ACTION_UP -> {
                    if (consumedBackKeyDown) {
                        consumedBackKeyDown = false
                        return true
                    }
                }
            }
            return super.onKeyEvent(event)
        }

        if (keyCode == KeyEvent.KEYCODE_POWER) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                if (modalActive.value) {
                    dismissModalOverlay()
                }
            }
            return super.onKeyEvent(event)
        }

        if (keyCode != KeyEvent.KEYCODE_VOLUME_UP && keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) {
            return super.onKeyEvent(event)
        }

        val isUp = keyCode == KeyEvent.KEYCODE_VOLUME_UP
        val direction = if (isUp) 1 else -1
        val hudAllowed = EdgeOrientationMapper.shouldShowSliderHud(isLandscape, currentSettings)
        val showSystemUi = EdgeOrientationMapper.shouldShowSystemVolumePanel(isLandscape, currentSettings)

        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount > 0) {
                    val wasConsumed = if (isUp) consumedUpKeyDown else consumedDownKeyDown
                    return if (wasConsumed) true else super.onKeyEvent(event)
                }

                if (shouldBypassVolumeInterception(currentSettings, isLandscape)) {
                    if (isUp) consumedUpKeyDown = false else consumedDownKeyDown = false
                    cancelHoldRepeatLoop(isUp = isUp)
                    return super.onKeyEvent(event)
                }

                val sequence = holdSequence.incrementAndGet()
                if (isUp) {
                    consumedUpKeyDown = true
                    cancelHoldRepeatLoop(isUp = false)
                } else {
                    consumedDownKeyDown = true
                    cancelHoldRepeatLoop(isUp = true)
                }
                keyHeldDown = true

                startAudioStepAndHoldLoop(
                    isUp = isUp,
                    direction = direction,
                    sequence = sequence,
                    streamType = AudioManager.STREAM_MUSIC,
                    initialDelayMs = 300L
                )
                serviceScope.launch(Dispatchers.Main) {
                    if (hudAllowed) {
                        handleHudTrigger(settings = currentSettings)
                    }
                }
                return true
            }

            KeyEvent.ACTION_UP -> {
                keyHeldDown = false
                holdSequence.incrementAndGet()
                cancelHoldRepeatLoop(isUp = true)
                cancelHoldRepeatLoop(isUp = false)
                val wasConsumed = if (isUp) {
                    val c = consumedUpKeyDown
                    consumedUpKeyDown = false
                    c
                } else {
                    val c = consumedDownKeyDown
                    consumedDownKeyDown = false
                    c
                }
                if (!wasConsumed) {
                    return super.onKeyEvent(event)
                }
                return true
            }
        }
        return super.onKeyEvent(event)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        _isServiceRunning.value = false
        keyHeldDown = false
        holdSequence.incrementAndGet()
        consumedUpKeyDown = false
        consumedDownKeyDown = false
        consumedBackKeyDown = false
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        _isServiceRunning.value = false
        keyHeldDown = false
        holdSequence.incrementAndGet()
        runCatching { displayManager.unregisterDisplayListener(displayListener) }
        runCatching { cameraManager.unregisterAvailabilityCallback(cameraAvailabilityCallback) }
        runCatching { cameraManager.unregisterTorchCallback(torchCallback) }
        if (::volumeController.isInitialized) {
            runCatching { volumeController.unregisterAudioModeListener() }
        }
        runCatching { unregisterReceiver(volumeReceiver) }
        runCatching { unregisterReceiver(powerScreenReceiver) }
        activeCameraIds.clear()
        torchActiveCameraIds.clear()
        audioScope?.cancel()
        audioScope = null
        audioHandler = null
        audioThread?.quitSafely()
        audioThread = null
        serviceScope.cancel()
        detachAllOverlays()
        lifecycleOwner.onDestroy()
        super.onDestroy()
    }

    private fun dismissModalOverlay() {
        if (!modalActive.value) return
        modalActive.value = false
        val latest = settingsRepository.settings.value
        if (hudActive.value) {
            resetHudAutoHide(latest.hudAutoHideMs)
        }
    }

    private fun closeMixerAndVolumeOverlaysOnScreenOff() {
        cancelHoldRepeatLoop(isUp = true)
        cancelHoldRepeatLoop(isUp = false)
        hudHideJob?.cancel()
        hudCollapseGoneJob?.cancel()
        modalCollapseGoneJob?.cancel()
        keyHeldDown = false
        consumedUpKeyDown = false
        consumedDownKeyDown = false
        consumedBackKeyDown = false
        hudScrubbing = false
        modalActive.value = false
        hudActive.value = false
        modalView?.visibility = View.GONE
        hudView?.visibility = View.GONE
    }

    private fun handleDisplayChange() {
        val (newRotation, newSize) = readDisplayRotationAndSize()
        val oldRotation = rotationState.value
        val oldSize = screenSizeState.value
        if (newRotation == oldRotation && newSize == oldSize) return

        cancelAllActiveAnimationsAndJobs()
        hudView?.visibility = View.GONE
        modalView?.visibility = View.GONE

        rotationResetEpoch.value += 1
        rotationState.value = newRotation
        screenSizeState.value = newSize

        reanchorOverlaysImmediately(settingsRepository.settings.value, newRotation)
    }

    private fun cancelAllActiveAnimationsAndJobs() {
        hudHideJob?.cancel()
        hudCollapseGoneJob?.cancel()
        modalCollapseGoneJob?.cancel()
        hudActive.value = false
        modalActive.value = false
        hudScrubbing = false
        settingsRepository.clearLivePreview()
    }

    private fun handleHudTrigger(settings: NudgeSettings) {
        showHud(settings.hudAutoHideMs)
    }

    private fun ensureAudioExecutor(): Handler {
        audioHandler?.let { return it }
        val thread = HandlerThread("NudgeAudio", Process.THREAD_PRIORITY_URGENT_DISPLAY).apply {
            start()
        }
        val handler = Handler(thread.looper)
        audioThread = thread
        audioHandler = handler
        audioScope = CoroutineScope(SupervisorJob() + handler.asCoroutineDispatcher("NudgeAudio"))
        return handler
    }

    private fun registerCameraAndAudioCallbacks(handler: Handler) {
        cameraPackages = resolveCameraPackages()
        publicCameraIds = runCatching { cameraManager.cameraIdList.toSet() }.getOrDefault(emptySet())
        runCatching { cameraManager.registerTorchCallback(torchCallback, handler) }
        runCatching { cameraManager.registerAvailabilityCallback(cameraAvailabilityCallback, handler) }
        val executor = Executor { command -> handler.post(command) }
        volumeController.registerAudioModeListener(executor)
    }

    private fun resolveCameraPackages(): Set<String> {
        val result = hashSetOf(
            "com.sec.android.app.camera",
            "com.google.android.GoogleCamera",
            "com.android.camera",
            "com.android.camera2",
            "com.oppo.camera",
            "com.oneplus.camera",
            "com.miui.camera",
            "com.sonyericsson.android.camera",
            "com.motorola.camera3",
            "net.sourceforge.opencamera"
        )
        val actions = arrayOf(
            MediaStore.ACTION_IMAGE_CAPTURE,
            MediaStore.ACTION_VIDEO_CAPTURE
        )
        for (action in actions) {
            val intent = Intent(action)
            val resolved = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    packageManager.queryIntentActivities(
                        intent,
                        PackageManager.ResolveInfoFlags.of(0L)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    packageManager.queryIntentActivities(intent, 0)
                }
            }.getOrDefault(emptyList())
            for (info in resolved) {
                info.activityInfo?.packageName?.takeIf { it.isNotBlank() }?.let(result::add)
            }
        }
        return result
    }

    private fun isCameraActive(): Boolean {
        val activeNonTorch = activeCameraIds.any { id -> !torchActiveCameraIds.contains(id) }
        val fg = currentForegroundPackage
        val foregroundCamera = fg.isNotEmpty() && cameraPackages.contains(fg)
        return activeNonTorch || foregroundCamera
    }

    private fun shouldBypassVolumeInterception(
        settings: NudgeSettings,
        isLandscape: Boolean
    ): Boolean {
        if (!isScreenInteractive) {
            if (!::powerManager.isInitialized || !powerManager.isInteractive) return true
            isScreenInteractive = true
        }
        if (isCameraActive()) return true
        if (volumeController.isCallOrRingtonePriorityFast()) return true
        return !EdgeOrientationMapper.shouldConsumeVolumeKey(isLandscape, settings)
    }

    private fun applyAudioVolumeStep(
        streamType: Int,
        direction: Int,
        settings: NudgeSettings,
        showSystemUi: Boolean,
        initialPress: Boolean
    ): Boolean {
        volumeController.refreshAudioModeCache()
        val minVol = volumeController.getStreamMin(streamType)
        val maxVol = volumeController.getStreamMax(streamType)
        val currentVol = volumeController.getStreamVolume(streamType)
        val nextVol = VolumeController.computeSteppedVolume(
            currentVolume = currentVol,
            direction = direction,
            volumeStep = settings.volumeStep,
            minVolume = minVol,
            maxVolume = maxVol
        )
        lastProgrammaticAudioTimeMs = SystemClock.uptimeMillis()
        val shouldWrite = nextVol != currentVol || showSystemUi
        val appliedVol = if (shouldWrite) {
            volumeController.setStreamIndex(
                streamType = streamType,
                targetIndex = nextVol,
                showSystemUi = showSystemUi,
                knownCurrent = currentVol
            )
        } else {
            currentVol
        }
        if (streamType == AudioManager.STREAM_MUSIC) {
            val span = (maxVol - minVol).coerceAtLeast(1)
            volumeFraction.value = ((appliedVol - minVol).toFloat() / span.toFloat()).coerceIn(0f, 1f)
        }
        if (settings.hapticsEnabled && (initialPress || appliedVol != currentVol)) {
            volumeController.triggerHaptic(strong = false)
        }
        return appliedVol != currentVol
    }

    private fun startAudioStepAndHoldLoop(
        isUp: Boolean,
        direction: Int,
        sequence: Long,
        streamType: Int,
        initialDelayMs: Long
    ) {
        val scope = audioScope ?: return
        val job = scope.launch {
            val initialSettings = settingsRepository.settings.value
            val initialLandscape = EdgeOrientationMapper.isLandscapeRotation(rotationState.value)
            applyAudioVolumeStep(
                streamType = streamType,
                direction = direction,
                settings = initialSettings,
                showSystemUi = EdgeOrientationMapper.shouldShowSystemVolumePanel(
                    initialLandscape,
                    initialSettings
                ),
                initialPress = true
            )
            delay(initialDelayMs)
            while (isActive && keyHeldDown && holdSequence.get() == sequence) {
                val latest = settingsRepository.settings.value
                val isLandscape = EdgeOrientationMapper.isLandscapeRotation(rotationState.value)
                val hudAllowed = EdgeOrientationMapper.shouldShowSliderHud(isLandscape, latest)
                val showSystemUi = EdgeOrientationMapper.shouldShowSystemVolumePanel(isLandscape, latest)
                val changed = applyAudioVolumeStep(
                    streamType = streamType,
                    direction = direction,
                    settings = latest,
                    showSystemUi = showSystemUi,
                    initialPress = false
                )
                if (hudAllowed && keyHeldDown && holdSequence.get() == sequence) {
                    withContext(Dispatchers.Main) {
                        if (keyHeldDown && holdSequence.get() == sequence) {
                            handleHudTrigger(settings = latest)
                        }
                    }
                }
                delay(if (changed) 75L else 90L)
            }
        }
        if (isUp) {
            upRepeatJob?.cancel()
            upRepeatJob = job
        } else {
            downRepeatJob?.cancel()
            downRepeatJob = job
        }
    }

    private fun cancelHoldRepeatLoop(isUp: Boolean) {
        if (isUp) {
            upRepeatJob?.cancel()
            upRepeatJob = null
        } else {
            downRepeatJob?.cancel()
            downRepeatJob = null
        }
    }

    private fun showHud(autoHideMs: Long) {
        if (!settingsRepository.settings.value.enableVolumeSliderHud) return
        hudCollapseGoneJob?.cancel()
        if (hudView?.visibility != View.VISIBLE) {
            hudView?.visibility = View.VISIBLE
        }
        hudActive.value = true
        resetHudAutoHide(autoHideMs)
    }

    private fun resetHudAutoHide(autoHideMs: Long) {
        hudHideJob?.cancel()
        if (hudScrubbing || modalActive.value) return
        hudHideJob = serviceScope.launch {
            delay(autoHideMs)
            if (!hudScrubbing && !modalActive.value) {
                hudActive.value = false
            }
        }
    }

    private fun setupOverlayWindows() {
        val initialSettings = settingsRepository.settings.value
        val initialRotation = rotationState.value

        if (initialSettings.enableVolumeSliderHud) {
            attachHudAndModalOverlays(initialSettings, initialRotation)
        }
    }

    private fun attachHudAndModalOverlays(settings: NudgeSettings, rotation: Int) {
        if (hudView == null) {
            val hudEdge = EdgeOrientationMapper.resolveHudScreenEdge(settings, rotation)
            val sliderSpec = buildHudLayoutSpec(settings, hudEdge, rotation, touchable = false)
            val sliderParams = specToLayoutParams(sliderSpec)
            val sliderView = createComposeOverlay {
                val latestSettings by settingsRepository.settings.collectAsState()
                val latestRotation by rotationState.collectAsState()
                val resetEpoch by rotationResetEpoch.collectAsState()
                val active by hudActive.collectAsState()
                val preview by settingsRepository.previewTarget.collectAsState()
                val volFraction by volumeFraction.collectAsState()

                val isLandscape = EdgeOrientationMapper.isLandscapeRotation(latestRotation)
                val hudAllowed = EdgeOrientationMapper.shouldShowSliderHud(isLandscape, latestSettings)
                val screenEdge = EdgeOrientationMapper.resolveHudScreenEdge(
                    latestSettings,
                    latestRotation
                )
                val visible = hudAllowed && (active || preview == PreviewTarget.HUD)

                VolumeSliderHud(
                    screenEdge = screenEdge,
                    hudWidthDp = latestSettings.hudWidthDp,
                    hudHeightDp = latestSettings.hudHeightDp,
                    hudCornerRadiusDp = latestSettings.hudCornerRadiusDp,
                    floatingMode = latestSettings.floatingMode,
                    edgeInsetDp = latestSettings.hudEdgeInsetDp,
                    volumeFraction = volFraction,
                    isVisible = visible,
                    resetEpoch = resetEpoch,
                    onVolumeScrub = { fraction ->
                        volumeFraction.value = fraction
                        val prevStep = volumeController.getStreamVolume(AudioManager.STREAM_MUSIC)
                        val nextStep = volumeController.setMusicVolumeFraction(fraction)
                        lastProgrammaticAudioTimeMs = SystemClock.uptimeMillis()
                        if (latestSettings.hapticsEnabled && nextStep != prevStep) {
                            volumeController.triggerHaptic(strong = false)
                        }
                    },
                    onScrubStateChanged = { scrubbing ->
                        hudScrubbing = scrubbing
                        if (scrubbing) {
                            hudHideJob?.cancel()
                        } else if (hudActive.value) {
                            resetHudAutoHide(latestSettings.hudAutoHideMs)
                        }
                    },
                    onOpenMenu = {
                        if (latestSettings.hapticsEnabled) {
                            volumeController.triggerHaptic(strong = true)
                        }
                        hudHideJob?.cancel()
                        modalOpenedTimeMs = SystemClock.uptimeMillis()
                        modalActive.value = true
                    },
                    interactive = visible,
                    modifier = Modifier.fillMaxSize()
                )
            }
            sliderView.visibility = View.GONE
            hudParams = sliderParams
            hudView = sliderView
            windowManager.addView(sliderView, sliderParams)
        }

        if (modalView == null) {
            val quickModalParams = createFullModalParams()
            val quickModalComposeView = createComposeOverlay {
                val isOpen by modalActive.collectAsState()
                val latestSettings by settingsRepository.settings.collectAsState()
                val volFraction by volumeFraction.collectAsState()

                NudgeTheme(darkTheme = !latestSettings.lightMode) {
                    QuickVolumeModal(
                        volumeController = volumeController,
                        musicFractionExternal = volFraction,
                        sliderWidthDp = latestSettings.hudWidthDp,
                        sliderHeightDp = latestSettings.hudHeightDp,
                        sliderCornerRadiusDp = latestSettings.hudCornerRadiusDp,
                        lightMode = latestSettings.lightMode,
                        hapticsEnabled = latestSettings.hapticsEnabled,
                        isVisible = isOpen,
                        onMusicFractionChanged = { fraction ->
                            volumeFraction.value = fraction
                            lastProgrammaticAudioTimeMs = SystemClock.uptimeMillis()
                        },
                        onOpenSettings = {
                            modalActive.value = false
                            hudActive.value = false
                            val intent = Intent(this, MainActivity::class.java).apply {
                                addFlags(
                                    Intent.FLAG_ACTIVITY_NEW_TASK or
                                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                                )
                            }
                            startActivity(intent)
                        },
                        onDismiss = {
                            dismissModalOverlay()
                        }
                    )
                }
            }
            val quickModalContainer = object : FrameLayout(this) {
                override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
                    if (ev?.actionMasked == MotionEvent.ACTION_OUTSIDE && modalActive.value) {
                        dismissModalOverlay()
                        return true
                    }
                    return super.dispatchTouchEvent(ev)
                }
            }.apply {
                lifecycleOwner.attachToView(this)
                addView(
                    quickModalComposeView,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                )
                visibility = View.GONE
            }
            modalParams = quickModalParams
            modalView = quickModalContainer
            windowManager.addView(quickModalContainer, quickModalParams)
        }
    }

    private fun detachHudAndModalOverlays() {
        hudActive.value = false
        modalActive.value = false
        hudHideJob?.cancel()
        hudCollapseGoneJob?.cancel()
        modalCollapseGoneJob?.cancel()
        removeOverlayView(hudView)
        removeOverlayView(modalView)
        hudView = null
        hudParams = null
        modalView = null
        modalParams = null
    }

    private fun detachAllOverlays() {
        removeOverlayView(hudView)
        removeOverlayView(modalView)
        hudView = null
        hudParams = null
        modalView = null
        modalParams = null
    }

    private fun observeOverlayLayout() {
        serviceScope.launch {
            settingsRepository.settings
                .combine(rotationState) { settings, rotation -> Pair(settings, rotation) }
                .combine(screenSizeState) { pair, size -> Triple(pair.first, pair.second, size) }
                .distinctUntilChanged()
                .collect { (settings, rotation, _) ->
                    val isLandscape = EdgeOrientationMapper.isLandscapeRotation(rotation)
                    val hudAllowed = EdgeOrientationMapper.shouldShowSliderHud(isLandscape, settings)

                    if (settings.enableVolumeSliderHud) {
                        attachHudAndModalOverlays(settings, rotation)
                    } else {
                        detachHudAndModalOverlays()
                    }

                    if (!hudAllowed && (hudActive.value || modalActive.value)) {
                        hudActive.value = false
                        modalActive.value = false
                    }

                    reanchorOverlaysImmediately(settings, rotation)
                }
        }

        serviceScope.launch {
            combine(
                settingsRepository.settings,
                hudActive,
                settingsRepository.previewTarget
            ) { settings, hud, preview ->
                HudTouchSnapshot(
                    settings = settings,
                    hudVisible = hud || preview == PreviewTarget.HUD
                )
            }.combine(rotationState) { snapshot, rotation ->
                Pair(snapshot, rotation)
            }.distinctUntilChanged().collect { (snapshot, rotation) ->
                val isLandscape = EdgeOrientationMapper.isLandscapeRotation(rotation)
                val hudAllowed = EdgeOrientationMapper.shouldShowSliderHud(isLandscape, snapshot.settings)

                val hView = hudView
                val hParams = hudParams
                if (hView != null && hParams != null) {
                    val effectiveVisible = hudAllowed && snapshot.hudVisible
                    if (effectiveVisible) {
                        hudCollapseGoneJob?.cancel()
                        if (hView.visibility != View.VISIBLE) {
                            hView.visibility = View.VISIBLE
                        }
                    } else if (hView.visibility == View.VISIBLE) {
                        hudCollapseGoneJob?.cancel()
                        hudCollapseGoneJob = serviceScope.launch {
                            delay(260L)
                            hudView?.visibility = View.GONE
                        }
                    }

                    val hudEdge = EdgeOrientationMapper.resolveHudScreenEdge(
                        snapshot.settings,
                        rotation
                    )
                    val hudSpec = buildHudLayoutSpec(
                        settings = snapshot.settings,
                        screenEdge = hudEdge,
                        rotation = rotation,
                        touchable = effectiveVisible
                    )
                    applyLayoutSpecIfChanged(hView, hParams, hudSpec)
                }
            }
        }

        serviceScope.launch {
            modalActive.collect { open ->
                val view = modalView ?: return@collect
                val params = modalParams ?: return@collect
                val desiredFlags = if (open) {
                    baseOverlayFlags() or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                } else {
                    baseOverlayFlags() or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                }
                if (open) {
                    modalCollapseGoneJob?.cancel()
                    if (view.visibility != View.VISIBLE) {
                        view.visibility = View.VISIBLE
                    }
                } else if (view.visibility == View.VISIBLE) {
                    modalCollapseGoneJob?.cancel()
                    modalCollapseGoneJob = serviceScope.launch {
                        delay(210L)
                        modalView?.visibility = View.GONE
                    }
                }
                if (params.flags != desiredFlags) {
                    params.flags = desiredFlags
                    if (view.isAttachedToWindow) {
                        runCatching { windowManager.updateViewLayout(view, params) }
                    }
                }
            }
        }
    }

    private fun reanchorOverlaysImmediately(settings: NudgeSettings, rotation: Int) {
        val hView = hudView
        val hParams = hudParams
        if (hView != null && hParams != null) {
            val isLandscape = EdgeOrientationMapper.isLandscapeRotation(rotation)
            val hudAllowed = EdgeOrientationMapper.shouldShowSliderHud(isLandscape, settings)
            val hudEdge = EdgeOrientationMapper.resolveHudScreenEdge(settings, rotation)
            val touchable = hudAllowed && (
                hudActive.value || settingsRepository.previewTarget.value == PreviewTarget.HUD
                )
            applyLayoutSpecIfChanged(
                hView,
                hParams,
                buildHudLayoutSpec(settings, hudEdge, rotation, touchable)
            )
        }
    }

    private fun applyLayoutSpecIfChanged(
        view: View,
        params: WindowManager.LayoutParams,
        desired: WindowLayoutSpec
    ) {
        if (params.width != desired.width ||
            params.height != desired.height ||
            params.gravity != desired.gravity ||
            params.x != desired.x ||
            params.y != desired.y ||
            params.flags != desired.flags
        ) {
            params.width = desired.width
            params.height = desired.height
            params.gravity = desired.gravity
            params.x = desired.x
            params.y = desired.y
            params.flags = desired.flags
            if (view.isAttachedToWindow) {
                runCatching { windowManager.updateViewLayout(view, params) }
            }
        }
    }

    private fun createComposeOverlay(content: @androidx.compose.runtime.Composable () -> Unit): ComposeView {
        return ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            lifecycleOwner.attachToView(this)
            setContent(content)
        }
    }

    private fun specToLayoutParams(spec: WindowLayoutSpec): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            spec.width,
            spec.height,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            spec.flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = spec.gravity
            x = spec.x
            y = spec.y
            applyEdgeCutoutAndInsets()
        }
    }

    private fun buildHudLayoutSpec(
        settings: NudgeSettings,
        screenEdge: ScreenEdge,
        rotation: Int,
        touchable: Boolean
    ): WindowLayoutSpec {
        val isVertical = screenEdge == ScreenEdge.LEFT || screenEdge == ScreenEdge.RIGHT
        val insetDp = settings.hudEdgeInsetDp
        val thicknessPx = dpToPx(computeHudCutoutThicknessDp(settings.hudWidthDp, insetDp))
        val spanPx = dpToPx(computeHudOuterSpanDp(settings.hudHeightDp))
        val width = if (isVertical) thicknessPx else spanPx
        val height = if (isVertical) spanPx else thicknessPx
        val (screenW, screenH) = screenSizeState.value

        val x: Int
        val y: Int
        if (isVertical) {
            x = 0
            y = EdgeOrientationMapper.computeHudTopOrLeftPx(
                settings = settings,
                rotation = rotation,
                axisLengthPx = screenH.toFloat(),
                elementSpanPx = height
            )
        } else {
            x = EdgeOrientationMapper.computeHudTopOrLeftPx(
                settings = settings,
                rotation = rotation,
                axisLengthPx = screenW.toFloat(),
                elementSpanPx = width
            )
            y = 0
        }

        val flags = if (touchable) {
            baseOverlayFlags()
        } else {
            baseOverlayFlags() or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        return WindowLayoutSpec(
            width = width,
            height = height,
            gravity = screenEdgeGravity(screenEdge),
            x = x,
            y = y,
            flags = flags
        )
    }

    private fun createFullModalParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            baseOverlayFlags() or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = 0
            y = 0
            applyEdgeCutoutAndInsets()
        }
    }

    private fun WindowManager.LayoutParams.applyEdgeCutoutAndInsets() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            fitInsetsTypes = 0
        }
    }

    private fun baseOverlayFlags(): Int {
        return WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
    }

    private fun screenEdgeGravity(screenEdge: ScreenEdge): Int {
        return when (screenEdge) {
            ScreenEdge.RIGHT -> Gravity.TOP or Gravity.RIGHT
            ScreenEdge.LEFT -> Gravity.TOP or Gravity.LEFT
            ScreenEdge.TOP -> Gravity.TOP or Gravity.LEFT
            ScreenEdge.BOTTOM -> Gravity.BOTTOM or Gravity.LEFT
        }
    }

    private fun dpToPx(dp: Float): Int {
        return ceil(dp * resources.displayMetrics.density).toInt().coerceAtLeast(1)
    }

    private fun readDisplayRotationAndSize(): Pair<Int, Pair<Int, Int>> {
        val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
        val rotation = display?.rotation ?: Surface.ROTATION_0
        val size = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            Pair(bounds.width(), bounds.height())
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
            Pair(metrics.widthPixels, metrics.heightPixels)
        }
        return Pair(rotation, size)
    }

    private fun removeOverlayView(view: View?) {
        if (view != null && view.isAttachedToWindow) {
            runCatching { windowManager.removeViewImmediate(view) }
        }
    }

    private data class WindowLayoutSpec(
        val width: Int,
        val height: Int,
        val gravity: Int,
        val x: Int,
        val y: Int,
        val flags: Int
    )

    private data class HudTouchSnapshot(
        val settings: NudgeSettings,
        val hudVisible: Boolean
    )

    companion object {
        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning = _isServiceRunning.asStateFlow()
    }
}