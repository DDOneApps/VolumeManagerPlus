package moe.chensi.volume

import android.accessibilityservice.AccessibilityButtonController
import android.accessibilityservice.AccessibilityButtonController.AccessibilityButtonCallback
import android.accessibilityservice.AccessibilityService
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import moe.chensi.volume.bubble.BUBBLE_SHADOW_PADDING_DP
import moe.chensi.volume.bubble.calculateBubbleLayout
import moe.chensi.volume.compose.AppVolumeList
import moe.chensi.volume.compose.StreamVolumeSlider
import moe.chensi.volume.system.ActivityTaskManagerProxy
import moe.chensi.volume.ui.theme.VolumeManagerTheme
import org.joor.Reflect
import java.util.Objects
import kotlin.math.roundToInt

@SuppressLint("AccessibilityPolicy")
class Service : AccessibilityService() {
    companion object {
        const val ACTION_SHOW_VIEW = "moe.chensi.volume.ACTION_SHOW_VIEW"
        const val ACTION_BUBBLE_SETTINGS_CHANGED = "moe.chensi.volume.ACTION_BUBBLE_SETTINGS_CHANGED"
        const val ACTION_BUBBLE_PREVIEW_MODE = "moe.chensi.volume.ACTION_BUBBLE_PREVIEW_MODE"
        const val EXTRA_BUBBLE_PREVIEW_ENABLED = "moe.chensi.volume.EXTRA_BUBBLE_PREVIEW_ENABLED"

        private const val TAG = "VolumeManager.Service"
        private const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"

        private const val OVERLAY_IDLE_TIMEOUT = 5000L
        private const val BUBBLE_IDLE_TIMEOUT = 2000L
        private const val ANIMATION_DURATION = 220L
    }

    private val windowManager: WindowManager by lazy {
        Objects.requireNonNull(getSystemService(WindowManager::class.java)!!)
    }
    private lateinit var manager: Manager
    val activityTaskManager by lazy { ActivityTaskManagerProxy(this) }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val hideOverlayRunnable = Runnable { hideOverlay() }
    private val hideBubbleRunnable = Runnable { hideBubble() }

    private var overlayView: View? = null
    private var overlayVisible = false
    private var overlayLifecycle: LifecycleRegistry? = null

    private var bubbleView: View? = null
    private var bubbleVisible = false
    private var bubbleLifecycle: LifecycleRegistry? = null
    private var bubblePreviewModeEnabled = false

    private val overlayLayoutParams by lazy {
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }
    }

    private val bubbleLayoutParams by lazy {
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
    }

    private fun installViewOwners(
        view: AbstractComposeView,
        onLifecycleCreated: (LifecycleRegistry) -> Unit
    ) {
        val owner = object : SavedStateRegistryOwner {
            private val lifecycleRegistry = LifecycleRegistry(this)
            private val savedStateRegistryController = SavedStateRegistryController.create(this)

            init {
                savedStateRegistryController.performRestore(null)
                lifecycleRegistry.currentState = Lifecycle.State.STARTED
                onLifecycleCreated(lifecycleRegistry)
            }

            override val lifecycle: Lifecycle
                get() = lifecycleRegistry

            override val savedStateRegistry: SavedStateRegistry
                get() = savedStateRegistryController.savedStateRegistry
        }

        view.setViewTreeLifecycleOwner(owner)
        view.setViewTreeSavedStateRegistryOwner(owner)
    }

    private fun createOverlayView(): View {
        return object : AbstractComposeView(this) {
            init {
                installViewOwners(this) { lifecycle -> overlayLifecycle = lifecycle }
            }

            override fun onAttachedToWindow() {
                super.onAttachedToWindow()

                Log.i(TAG, "overlay attached manufacturer: ${Build.MANUFACTURER}")
                @Suppress("SpellCheckingInspection")
                if (windowManager.isCrossWindowBlurEnabled && isHardwareAccelerated && Build.MANUFACTURER != "realme") {
                    background =
                        Reflect.on(rootSurfaceControl).call("createBackgroundBlurDrawable").apply {
                            call("setBlurRadius", 200)
                            call("setCornerRadius", 40f)
                        }.get()
                }

                startOverlayIdleTimer()
            }

            @SuppressLint("ClickableViewAccessibility")
            override fun onTouchEvent(event: MotionEvent): Boolean {
                if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                    hideOverlay()
                    return true
                }
                return super.onTouchEvent(event)
            }

            @Composable
            override fun Content() {
                VolumeManagerTheme {
                    Surface(color = Color.Transparent, contentColor = Color.White) {
                        Column(
                            modifier = Modifier
                                .background(Color(1f, 1f, 1f, 0.3f), RoundedCornerShape(40f))
                                .padding(20.dp, 16.dp)
                        ) {
                            AppVolumeList(
                                apps = manager.apps.values,
                                showAll = false,
                                onChange = ::startOverlayIdleTimer
                            ) {
                                item(AudioManager.STREAM_MUSIC) {
                                    StreamVolumeSlider(
                                        AudioManager.STREAM_MUSIC,
                                        Icons.Default.MusicNote,
                                        "Music",
                                        manager.audioManager,
                                        onChange = ::startOverlayIdleTimer
                                    )
                                }

                                item(AudioManager.STREAM_NOTIFICATION) {
                                    StreamVolumeSlider(
                                        AudioManager.STREAM_NOTIFICATION,
                                        Icons.Default.Notifications,
                                        "Notifications",
                                        manager.audioManager,
                                        onChange = ::startOverlayIdleTimer
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun createBubbleView(): View {
        return object : AbstractComposeView(this) {
            init {
                installViewOwners(this) { lifecycle -> bubbleLifecycle = lifecycle }
            }

            @Composable
            override fun Content() {
                val preferences = manager.bubblePreferences
                val shadowEnabled = preferences.shadowEnabled
                val bubblePadding = if (shadowEnabled) BUBBLE_SHADOW_PADDING_DP.dp else 0.dp

                VolumeManagerTheme {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bubblePadding),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onSecondaryContainer,
                            shadowElevation = if (shadowEnabled) 8.dp else 0.dp,
                            tonalElevation = if (shadowEnabled) 4.dp else 0.dp,
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable {
                                    showOverlay()
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = "Open volume manager"
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun animateIn(view: View) {
        view.animate().cancel()
        view.alpha = 0f
        view.scaleX = 0.9f
        view.scaleY = 0.9f
        view.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(ANIMATION_DURATION)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .setListener(null)
            .start()
    }

    private fun animateOut(view: View, onEnd: () -> Unit) {
        view.animate().cancel()
        view.animate()
            .alpha(0f)
            .scaleX(0.9f)
            .scaleY(0.9f)
            .setDuration(ANIMATION_DURATION)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    onEnd()
                }
            })
            .start()
    }

    private fun startOverlayIdleTimer() {
        mainHandler.removeCallbacks(hideOverlayRunnable)
        mainHandler.postDelayed(hideOverlayRunnable, OVERLAY_IDLE_TIMEOUT)
    }

    private fun startBubbleIdleTimer() {
        if (bubblePreviewModeEnabled) {
            mainHandler.removeCallbacks(hideBubbleRunnable)
            return
        }

        mainHandler.removeCallbacks(hideBubbleRunnable)
        mainHandler.postDelayed(hideBubbleRunnable, BUBBLE_IDLE_TIMEOUT)
    }

    private fun showOverlay() {
        hideBubble()

        if (overlayView == null) {
            overlayView = createOverlayView()
            windowManager.addView(overlayView, overlayLayoutParams)
        }

        if (!overlayVisible) {
            overlayVisible = true
            animateIn(overlayView!!)
        }

        startOverlayIdleTimer()
    }

    private fun hideOverlay() {
        if (!overlayVisible) {
            return
        }

        overlayVisible = false
        mainHandler.removeCallbacks(hideOverlayRunnable)

        val target = overlayView ?: return
        animateOut(target) {
            if (overlayVisible) {
                return@animateOut
            }

            try {
                target.background = null
                overlayLifecycle?.currentState = Lifecycle.State.DESTROYED
                windowManager.removeView(target)
            } catch (_: Exception) {
            } finally {
                overlayView = null
                overlayLifecycle = null
                if (bubblePreviewModeEnabled) {
                    showBubble()
                }
            }
        }
    }

    private fun updateBubbleLayout() {
        val preferences = manager.bubblePreferences
        val width = resources.displayMetrics.widthPixels
        val height = resources.displayMetrics.heightPixels
        val shadowPaddingPx = if (preferences.shadowEnabled) {
            (resources.displayMetrics.density * BUBBLE_SHADOW_PADDING_DP).roundToInt()
        } else {
            0
        }
        val layout = calculateBubbleLayout(
            widthPx = width,
            heightPx = height,
            density = resources.displayMetrics.density,
            sizeScale = preferences.sizeScale,
            horizontal = preferences.horizontal,
            vertical = preferences.vertical
        )
        val windowWidth = layout.sizePx + shadowPaddingPx * 2
        val windowHeight = layout.sizePx + shadowPaddingPx * 2
        bubbleLayoutParams.width = windowWidth
        bubbleLayoutParams.height = windowHeight
        bubbleLayoutParams.x =
            (layout.xPx - shadowPaddingPx).coerceIn(0, (width - windowWidth).coerceAtLeast(0))
        bubbleLayoutParams.y =
            (layout.yPx - shadowPaddingPx).coerceIn(0, (height - windowHeight).coerceAtLeast(0))

        val target = bubbleView
        if (target != null) {
            windowManager.updateViewLayout(target, bubbleLayoutParams)
        }
    }

    private fun showBubble() {
        if (overlayVisible) {
            startOverlayIdleTimer()
            return
        }

        updateBubbleLayout()

        if (bubbleView == null) {
            bubbleView = createBubbleView()
            windowManager.addView(bubbleView, bubbleLayoutParams)
        }

        if (!bubbleVisible) {
            bubbleVisible = true
            animateIn(bubbleView!!)
        }

        startBubbleIdleTimer()
    }

    private fun hideBubble() {
        if (!bubbleVisible) {
            return
        }

        bubbleVisible = false
        mainHandler.removeCallbacks(hideBubbleRunnable)

        val target = bubbleView ?: return
        animateOut(target) {
            if (bubbleVisible) {
                return@animateOut
            }

            try {
                bubbleLifecycle?.currentState = Lifecycle.State.DESTROYED
                windowManager.removeView(target)
            } catch (_: Exception) {
            } finally {
                bubbleView = null
                bubbleLifecycle = null
            }
        }
    }

    private fun shouldIgnoreForegroundTaskVolumeKeys(): Boolean {
        val task = activityTaskManager.getForegroundTask()
        if (task != null) {
            val app = manager.apps[task.app]
            if (app != null && app.disableVolumeButtons) {
                return true
            }
        }
        return false
    }

    private val accessibilityButtonCallback = object : AccessibilityButtonCallback() {
        override fun onClicked(controller: AccessibilityButtonController?) {
            if (manager.shizukuStatus == Manager.ShizukuStatus.Connected) {
                showOverlay()
            }
        }
    }

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_SHOW_VIEW -> showOverlay()
                ACTION_BUBBLE_SETTINGS_CHANGED -> {
                    updateBubbleLayout()
                    if (bubblePreviewModeEnabled && !overlayVisible) {
                        showBubble()
                    }
                }
                ACTION_BUBBLE_PREVIEW_MODE -> {
                    bubblePreviewModeEnabled =
                        intent.getBooleanExtra(EXTRA_BUBBLE_PREVIEW_ENABLED, false)
                    if (bubblePreviewModeEnabled && !overlayVisible) {
                        showBubble()
                    } else if (!bubblePreviewModeEnabled) {
                        hideBubble()
                    }
                }
                VOLUME_CHANGED_ACTION -> if (bubbleVisible) startBubbleIdleTimer()
            }
        }
    }

    override fun onServiceConnected() {
        Log.i(TAG, "onServiceConnected")

        val application = super.getApplication() as MyApplication
        manager = application.manager

        accessibilityButtonController.registerAccessibilityButtonCallback(accessibilityButtonCallback)

        val filter = IntentFilter().apply {
            addAction(ACTION_SHOW_VIEW)
            addAction(ACTION_BUBBLE_SETTINGS_CHANGED)
            addAction(ACTION_BUBBLE_PREVIEW_MODE)
            addAction(VOLUME_CHANGED_ACTION)
        }
        registerReceiver(broadcastReceiver, filter, RECEIVER_NOT_EXPORTED)

        Log.i(TAG, "onServiceConnected done ${serviceInfo.capabilities.toString(2)}")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {}

    override fun onInterrupt() {
        Log.i(TAG, "onInterrupt")
    }

    override fun onDestroy() {
        super.onDestroy()

        Log.i(TAG, "onDestroy")
        Toast.makeText(this, "Accessibility service died!", Toast.LENGTH_SHORT).show()

        mainHandler.removeCallbacks(hideOverlayRunnable)
        mainHandler.removeCallbacks(hideBubbleRunnable)
        hideOverlay()
        hideBubble()

        try {
            accessibilityButtonController.unregisterAccessibilityButtonCallback(accessibilityButtonCallback)
        } catch (_: Exception) {
        }
        unregisterReceiver(broadcastReceiver)
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_VOLUME_UP && event.keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) {
            return false
        }

        if (manager.shizukuStatus != Manager.ShizukuStatus.Connected) {
            return false
        }

        if (shouldIgnoreForegroundTaskVolumeKeys()) {
            return false
        }

        if (event.action == KeyEvent.ACTION_DOWN) {
            showBubble()
        }

        // Let the system handle the key so Android's default slider stays visible.
        return false
    }
}
