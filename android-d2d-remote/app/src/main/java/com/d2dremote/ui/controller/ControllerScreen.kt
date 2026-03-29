package com.d2dremote.ui.controller

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.d2dremote.model.ConnectionState
import com.d2dremote.ui.components.StatusBadge
import com.d2dremote.ui.theme.Primary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControllerScreen(
    onNavigateBack: () -> Unit,
    viewModel: ControllerViewModel = viewModel()
) {
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val targetIp by viewModel.targetIp.collectAsStateWithLifecycle()
    val pairingCode by viewModel.pairingCode.collectAsStateWithLifecycle()
    val isInRemoteView by viewModel.isInRemoteView.collectAsStateWithLifecycle()

    AnimatedContent(
        targetState = isInRemoteView,
        transitionSpec = {
            (slideInHorizontally { it } + fadeIn()) togetherWith
                    (slideOutHorizontally { -it } + fadeOut())
        },
        label = "controller_content"
    ) { inRemoteView ->
        if (inRemoteView) {
            RemoteViewScreen(
                viewModel = viewModel,
                onBack = { viewModel.goBackFromRemoteView() }
            )
        } else {
            ControllerConnectScreen(
                targetIp = targetIp,
                pairingCode = pairingCode,
                connectionState = connectionState,
                onIpChanged = { viewModel.updateTargetIp(it) },
                onPairingCodeChanged = { viewModel.updatePairingCode(it) },
                onConnect = { viewModel.connect() },
                onDisconnect = { viewModel.disconnect() },
                onNavigateBack = onNavigateBack
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ControllerConnectScreen(
    targetIp: String,
    pairingCode: String,
    connectionState: ConnectionState,
    onIpChanged: (String) -> Unit,
    onPairingCodeChanged: (String) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CastConnected,
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "Controller Mode",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = Color.White
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        brush = Brush.linearGradient(
                            listOf(
                                Primary.copy(alpha = 0.05f),
                                Primary.copy(alpha = 0.02f)
                            )
                        )
                    )
                    .padding(24.dp)
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Wifi,
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Target Device IP",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = targetIp,
                        onValueChange = onIpChanged,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                "192.168.1.100",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Go
                        ),
                        keyboardActions = KeyboardActions(
                            onGo = { onConnect() }
                        ),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White
                        ),
                        enabled = connectionState !is ConnectionState.Connecting
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        brush = Brush.linearGradient(
                            listOf(
                                Primary.copy(alpha = 0.03f),
                                Primary.copy(alpha = 0.06f)
                            )
                        )
                    )
                    .padding(24.dp)
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Key,
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Pairing Code",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = pairingCode,
                        onValueChange = { if (it.length <= 6) onPairingCodeChanged(it) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                "6-digit code",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Go
                        ),
                        keyboardActions = KeyboardActions(
                            onGo = { onConnect() }
                        ),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White
                        ),
                        enabled = connectionState !is ConnectionState.Connecting
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFFF8FAFC))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Connection Status",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                StatusBadge(
                    text = when (connectionState) {
                        is ConnectionState.Idle -> "Not connected"
                        is ConnectionState.Connecting -> "Connecting..."
                        is ConnectionState.Connected -> "Connected"
                        is ConnectionState.Disconnected -> "Disconnected"
                        is ConnectionState.Error -> (connectionState as ConnectionState.Error).message
                    },
                    isActive = connectionState is ConnectionState.Connected
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    when (connectionState) {
                        is ConnectionState.Connected, is ConnectionState.Connecting -> onDisconnect()
                        else -> onConnect()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .animateContentSize(),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = when (connectionState) {
                        is ConnectionState.Connected, is ConnectionState.Connecting ->
                            MaterialTheme.colorScheme.error
                        else -> Primary
                    }
                ),
                enabled = targetIp.isNotBlank() && pairingCode.length == 6
            ) {
                when (connectionState) {
                    is ConnectionState.Connecting -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Connecting...", style = MaterialTheme.typography.titleMedium)
                    }
                    is ConnectionState.Connected -> {
                        Icon(
                            imageVector = Icons.Rounded.LinkOff,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Disconnect", style = MaterialTheme.typography.titleMedium)
                    }
                    else -> {
                        Icon(
                            imageVector = Icons.Rounded.Link,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Connect", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
