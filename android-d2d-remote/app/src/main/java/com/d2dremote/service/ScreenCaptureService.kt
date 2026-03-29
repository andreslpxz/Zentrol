package com.d2dremote.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import com.d2dremote.MainActivity
import com.d2dremote.R
import com.d2dremote.network.VideoStreamServer
import kotlinx.coroutines.*

class ScreenCaptureService : Service() {

    companion object {
        private const val TAG = "ScreenCaptureService"
        private const val CHANNEL_ID = "screen_capture_channel"
        private const val NOTIFICATION_ID = 1001

        private const val ACTION_START = "com.d2dremote.START_CAPTURE"
        private const val ACTION_STOP = "com.d2dremote.STOP_CAPTURE"
        private const val EXTRA_RESULT_CODE = "result_code"
        private const val EXTRA_RESULT_DATA = "result_data"
        private const val EXTRA_PAIRING_CODE = "pairing_code"

        var videoStreamServer: VideoStreamServer? = null
            private set

        @Volatile
        var isServiceRunning = false
            private set

        var onServiceStateChanged: ((Boolean) -> Unit)? = null

        fun startCapture(context: Context, resultCode: Int, resultData: Intent, pairingCode: String? = null) {
            val intent = Intent(context, ScreenCaptureService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, resultData)
                putExtra(EXTRA_PAIRING_CODE, pairingCode)
            }
            context.startForegroundService(intent)
        }

        fun stopCapture(context: Context) {
            val intent = Intent(context, ScreenCaptureService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var encoder: VideoEncoder? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var encoderJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1)
                @Suppress("DEPRECATION")
                val resultData = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
                val pairingCode = intent.getStringExtra(EXTRA_PAIRING_CODE)
                if (resultData != null) {
                    startForeground(NOTIFICATION_ID, createNotification())
                    startProjection(resultCode, resultData, pairingCode)
                }
            }
            ACTION_STOP -> {
                stopProjection()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startProjection(resultCode: Int, resultData: Intent, pairingCode: String? = null) {
        val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, resultData)

        if (mediaProjection == null) {
            Log.e(TAG, "Failed to get MediaProjection")
            stopSelf()
            return
        }

        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)

        val screenWidth = metrics.widthPixels
        val screenHeight = metrics.heightPixels
        val screenDensity = metrics.densityDpi

        val captureWidth = screenWidth / 2
        val captureHeight = screenHeight / 2

        val server = VideoStreamServer()
        videoStreamServer = server
        server.start(serviceScope, pairingCode)

        server.onClientConnected = {
            server.sendScreenInfo(screenWidth, screenHeight, screenDensity)
        }

        encoder = VideoEncoder(captureWidth, captureHeight) { data, offset, size ->
            server.sendFrame(data, offset, size)
        }
        encoder?.start()

        val surface = encoder?.inputSurface
        if (surface == null) {
            Log.e(TAG, "Encoder surface is null")
            stopProjection()
            stopSelf()
            return
        }

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "D2DRemote",
            captureWidth,
            captureHeight,
            screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            surface,
            null,
            null
        )

        encoderJob = serviceScope.launch {
            while (isActive) {
                encoder?.drainEncoder()
                delay(5)
            }
        }

        mediaProjection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                stopProjection()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }, null)

        isServiceRunning = true
        onServiceStateChanged?.invoke(true)
        Log.i(TAG, "Screen capture started: ${captureWidth}x${captureHeight}")
    }

    private fun stopProjection() {
        isServiceRunning = false
        onServiceStateChanged?.invoke(false)

        encoderJob?.cancel()
        encoder?.stop()
        encoder = null

        virtualDisplay?.release()
        virtualDisplay = null

        mediaProjection?.stop()
        mediaProjection = null

        videoStreamServer?.stop()
        videoStreamServer = null

        Log.i(TAG, "Screen capture stopped")
    }

    override fun onDestroy() {
        stopProjection()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_description)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.capture_notification_title))
            .setContentText(getString(R.string.capture_notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}
