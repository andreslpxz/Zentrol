package com.d2dremote.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.d2dremote.model.TouchEvent
import com.d2dremote.network.ControlServer
import kotlinx.coroutines.*

class TouchAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "TouchA11yService"

        @Volatile
        var instance: TouchAccessibilityService? = null
            private set

        @Volatile
        var isServiceEnabled = false
            private set

        @Volatile
        var pendingControlServerStart = false

        @Volatile
        var pendingPairingCode: String? = null

        var onServiceStateChanged: ((Boolean) -> Unit)? = null
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var controlServer: ControlServer? = null
    private var currentPath: Path? = null
    private var gestureStartTime = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        isServiceEnabled = true
        onServiceStateChanged?.invoke(true)
        Log.i(TAG, "Accessibility service connected")

        if (pendingControlServerStart) {
            pendingControlServerStart = false
            startControlServer(pendingPairingCode)
            pendingPairingCode = null
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted")
    }

    fun startControlServer(pairingCode: String? = null) {
        controlServer?.stop()
        controlServer = ControlServer().apply {
            onTouchEvent = { event -> handleTouchEvent(event) }
            onError = { msg -> Log.e(TAG, "Control server error: $msg") }
            start(serviceScope, pairingCode)
        }
        Log.i(TAG, "Control server started from accessibility service")
    }

    fun stopControlServer() {
        controlServer?.stop()
        controlServer = null
        Log.i(TAG, "Control server stopped")
    }

    private fun handleTouchEvent(event: TouchEvent) {
        when (event.type) {
            TouchEvent.ACTION_DOWN -> {
                currentPath = Path().apply { moveTo(event.x, event.y) }
                gestureStartTime = System.currentTimeMillis()
            }
            TouchEvent.ACTION_MOVE -> {
                currentPath?.lineTo(event.x, event.y)
            }
            TouchEvent.ACTION_UP -> {
                val path = currentPath ?: return
                path.lineTo(event.x, event.y)
                val duration = System.currentTimeMillis() - gestureStartTime
                val gestureDuration = maxOf(duration, 50L)

                dispatchGestureSafely(path, gestureDuration)
                currentPath = null
            }
        }
    }

    private fun dispatchGestureSafely(path: Path, duration: Long) {
        try {
            val stroke = GestureDescription.StrokeDescription(
                path, 0, duration
            )
            val gesture = GestureDescription.Builder()
                .addStroke(stroke)
                .build()

            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Log.d(TAG, "Gesture completed")
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.w(TAG, "Gesture cancelled")
                }
            }, null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to dispatch gesture: ${e.message}")
        }
    }

    override fun onDestroy() {
        stopControlServer()
        instance = null
        isServiceEnabled = false
        onServiceStateChanged?.invoke(false)
        serviceScope.cancel()
        super.onDestroy()
        Log.i(TAG, "Accessibility service destroyed")
    }
}
