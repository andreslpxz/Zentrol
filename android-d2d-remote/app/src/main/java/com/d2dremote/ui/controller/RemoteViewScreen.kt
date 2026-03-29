package com.d2dremote.ui.controller

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.d2dremote.model.ScreenInfo
import com.d2dremote.model.TouchEvent
import com.d2dremote.ui.theme.Primary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteViewScreen(
    viewModel: ControllerViewModel,
    onBack: () -> Unit
) {
    val screenInfo by viewModel.screenInfo.collectAsStateWithLifecycle()
    var viewSize by remember { mutableStateOf(IntSize.Zero) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Cast,
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Remote View",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = Color.Black
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            val info = screenInfo
            val aspectRatio = if (info != null && info.height > 0) {
                info.width.toFloat() / info.height.toFloat()
            } else {
                9f / 16f
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .aspectRatio(aspectRatio)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.DarkGray)
                    .onSizeChanged { viewSize = it }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                viewModel.sendTouchEvent(
                                    TouchEvent.ACTION_DOWN,
                                    offset.x, offset.y,
                                    viewSize.width.toFloat(),
                                    viewSize.height.toFloat()
                                )
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                viewModel.sendTouchEvent(
                                    TouchEvent.ACTION_MOVE,
                                    change.position.x, change.position.y,
                                    viewSize.width.toFloat(),
                                    viewSize.height.toFloat()
                                )
                            },
                            onDragEnd = {
                            },
                            onDragCancel = {
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: continue
                                if (change.pressed && !change.previousPressed) {
                                    viewModel.sendTouchEvent(
                                        TouchEvent.ACTION_DOWN,
                                        change.position.x, change.position.y,
                                        viewSize.width.toFloat(),
                                        viewSize.height.toFloat()
                                    )
                                } else if (!change.pressed && change.previousPressed) {
                                    viewModel.sendTouchEvent(
                                        TouchEvent.ACTION_UP,
                                        change.position.x, change.position.y,
                                        viewSize.width.toFloat(),
                                        viewSize.height.toFloat()
                                    )
                                }
                            }
                        }
                    }
            ) {
                AndroidView(
                    factory = { ctx ->
                        SurfaceView(ctx).apply {
                            holder.addCallback(object : SurfaceHolder.Callback {
                                override fun surfaceCreated(holder: SurfaceHolder) {
                                    viewModel.initDecoder(holder.surface)
                                }

                                override fun surfaceChanged(
                                    holder: SurfaceHolder,
                                    format: Int,
                                    width: Int,
                                    height: Int
                                ) {}

                                override fun surfaceDestroyed(holder: SurfaceHolder) {}
                            })
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            if (info == null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Waiting for video stream...",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}
