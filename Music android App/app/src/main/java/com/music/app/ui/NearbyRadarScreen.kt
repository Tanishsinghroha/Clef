package com.music.app.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.app.R
import com.music.app.api.DiscoveredReceiver
import com.music.app.api.NearbyShareController
import com.music.app.api.NearbyShareRole
import com.music.app.api.NearbyShareState

/**
 * Radar discovery and transfer screen for offline music sharing.
 * Displays animated concentric radar waves, discovered nearby peers,
 * PIN verification modal, and live file transfer progress.
 */
@Composable
fun NearbyRadarScreen(
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val role = NearbyShareController.role
    val state = NearbyShareController.state
    val statusMessage = NearbyShareController.statusMessage
    val discoveredList = NearbyShareController.discoveredReceivers
    val progress = NearbyShareController.transferProgress
    val authPin = NearbyShareController.authPin
    val connectedName = NearbyShareController.connectedEndpointName
    val completionMsg = NearbyShareController.completionMessage

    // Cleanup Nearby connections when screen is closed
    DisposableEffect(Unit) {
        onDispose {
            if (NearbyShareController.state != NearbyShareState.COMPLETED) {
                NearbyShareController.stopAll(context)
            }
        }
    }

    BackHandler {
        NearbyShareController.stopAll(context)
        onClose()
    }

    // PIN Authentication Dialog
    if (state == NearbyShareState.CONFIRMING_PIN && authPin != null) {
        AlertDialog(
            onDismissRequest = { NearbyShareController.rejectConnection(context) },
            containerColor = Color(0xFF1C222C),
            shape = RoundedCornerShape(24.dp),
            title = {
                Text(
                    text = "Confirm Connection",
                    color = Color(0xFFE5DED5),
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Do you want to connect with ${connectedName ?: "nearby device"}?",
                        color = Color(0xFFA0AAB5),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Surface(
                        color = Color(0xFF2E3A46).copy(alpha = 0.6f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFE5DED5).copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = authPin,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE5DED5),
                            letterSpacing = 6.sp,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Make sure both devices show this same code",
                        color = Color(0xFFA0AAB5).copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { NearbyShareController.acceptConnection(context) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4B5A69)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Accept & Transfer", color = Color(0xFFE5DED5), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { NearbyShareController.rejectConnection(context) }
                ) {
                    Text("Decline", color = Color(0xFFFF6B6B))
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // App background image texture
        Image(
            painter = painterResource(id = R.drawable.background),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Dark gradient overlay matching app theme
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF141820).copy(alpha = 0.95f),
                            Color(0xFF1C222C).copy(alpha = 0.97f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar with Close button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = if (role == NearbyShareRole.RECEIVER) "Receive Music" else "Sending Music",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE5DED5)
                    )
                    Text(
                        text = "Device: ${NearbyShareController.getLocalDeviceName()}",
                        fontSize = 13.sp,
                        color = Color(0xFFA0AAB5)
                    )
                }

                IconButton(
                    onClick = {
                        NearbyShareController.stopAll(context)
                        onClose()
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color.White.copy(alpha = 0.12f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color(0xFFE5DED5),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            when (state) {
                NearbyShareState.TRANSFERRING -> {
                    // LIVE FILE TRANSFER SCREEN
                    TransferProgressCard(
                        progress = progress,
                        statusMessage = statusMessage,
                        connectedName = connectedName,
                        isReceiver = (role == NearbyShareRole.RECEIVER)
                    )
                }
                NearbyShareState.COMPLETED -> {
                    // SUCCESS SCREEN
                    TransferSuccessView(
                        message = completionMsg ?: "Transfer finished successfully!",
                        onDone = {
                            NearbyShareController.resetState()
                            onClose()
                        }
                    )
                }
                NearbyShareState.ERROR -> {
                    // ERROR SCREEN
                    TransferErrorView(
                        errorMessage = statusMessage,
                        onRetry = {
                            if (role == NearbyShareRole.RECEIVER) {
                                NearbyShareController.startReceiving(context)
                            } else {
                                onClose()
                            }
                        },
                        onClose = {
                            NearbyShareController.stopAll(context)
                            onClose()
                        }
                    )
                }
                else -> {
                    // RADAR VIEW (Advertising or Discovering)
                    RadarAnimationView(
                        role = role,
                        statusMessage = statusMessage
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    if (role == NearbyShareRole.SENDER) {
                        // SENDER MODE: List of discovered receivers
                        Text(
                            text = if (discoveredList.isEmpty()) "Scanning for nearby receivers..." else "Select Device to Send:",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFE5DED5),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        if (discoveredList.isEmpty()) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                color = Color.White.copy(alpha = 0.05f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_paper_plane),
                                        contentDescription = "Scanning",
                                        tint = Color(0xFFE5DED5).copy(alpha = 0.8f),
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "Ask your friend to open the app and tap 'Receive'",
                                        fontSize = 13.sp,
                                        color = Color(0xFFA0AAB5),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(discoveredList) { receiver ->
                                    DiscoveredDeviceItem(
                                        receiver = receiver,
                                        onClick = {
                                            NearbyShareController.connectToReceiver(context, receiver)
                                        }
                                    )
                                }
                            }
                        }
                    } else {
                        // RECEIVER MODE: Instruction Card
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            color = Color(0xFF2E3A46).copy(alpha = 0.40f),
                            border = BorderStroke(1.dp, Color(0xFFE5DED5).copy(alpha = 0.20f))
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_receive),
                                    contentDescription = "Receive",
                                    tint = Color(0xFFE5DED5),
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Ready to Receive Music",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFE5DED5)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Your device is broadcasting offline. Ask the sender to select this device from their screen.",
                                    fontSize = 13.sp,
                                    color = Color(0xFFA0AAB5),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/**
 * Animated concentric radar ripples with center device avatar.
 */
@Composable
private fun RadarAnimationView(
    role: NearbyShareRole,
    statusMessage: String
) {
    val transition = rememberInfiniteTransition(label = "radarTransition")

    val ripple1Scale by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "r1Scale"
    )
    val ripple1Alpha by transition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "r1Alpha"
    )

    val ripple2Scale by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, delayMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "r2Scale"
    )
    val ripple2Alpha by transition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, delayMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "r2Alpha"
    )

    val ripple3Scale by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, delayMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "r3Scale"
    )
    val ripple3Alpha by transition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, delayMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "r3Alpha"
    )

    val radarColor = if (role == NearbyShareRole.RECEIVER) Color(0xFF4B5A69) else Color(0xFF354250)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier.size(240.dp),
            contentAlignment = Alignment.Center
        ) {
            // Ripple 1
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .scale(ripple1Scale)
                    .clip(CircleShape)
                    .background(radarColor.copy(alpha = ripple1Alpha * 0.35f))
                    .border(1.5.dp, Color(0xFFE5DED5).copy(alpha = ripple1Alpha * 0.4f), CircleShape)
            )

            // Ripple 2
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .scale(ripple2Scale)
                    .clip(CircleShape)
                    .background(radarColor.copy(alpha = ripple2Alpha * 0.35f))
                    .border(1.5.dp, Color(0xFFE5DED5).copy(alpha = ripple2Alpha * 0.4f), CircleShape)
            )

            // Ripple 3
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .scale(ripple3Scale)
                    .clip(CircleShape)
                    .background(radarColor.copy(alpha = ripple3Alpha * 0.35f))
                    .border(1.5.dp, Color(0xFFE5DED5).copy(alpha = ripple3Alpha * 0.4f), CircleShape)
            )

            // Center Device Avatar
            Surface(
                shape = CircleShape,
                color = Color(0xFF2E3A46),
                shadowElevation = 16.dp,
                modifier = Modifier
                    .size(76.dp)
                    .border(2.dp, Color(0xFFE5DED5).copy(alpha = 0.6f), CircleShape)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (role == NearbyShareRole.RECEIVER) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_receive),
                            contentDescription = "Receive",
                            tint = Color(0xFFE5DED5),
                            modifier = Modifier.size(32.dp)
                        )
                    } else {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_paper_plane),
                            contentDescription = "Send",
                            tint = Color(0xFFE5DED5),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White.copy(alpha = 0.08f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
        ) {
            Text(
                text = statusMessage,
                fontSize = 13.sp,
                color = Color(0xFFE5DED5),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }
    }
}

/**
 * Discovered Device Card for Sender to tap and initiate connection.
 */
@Composable
private fun DiscoveredDeviceItem(
    receiver: DiscoveredReceiver,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .border(
                BorderStroke(
                    1.dp,
                    Brush.horizontalGradient(
                        listOf(Color(0xFFE5DED5).copy(alpha = 0.3f), Color.White.copy(alpha = 0.08f))
                    )
                ),
                RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C222C))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF2E3A46),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("📱", fontSize = 22.sp)
                    }
                }

                Column {
                    Text(
                        text = receiver.endpointName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE5DED5)
                    )
                    Text(
                        text = "Available nearby",
                        fontSize = 12.sp,
                        color = Color(0xFFA0AAB5)
                    )
                }
            }

            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4B5A69)),
                shape = RoundedCornerShape(12.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_paper_plane),
                        contentDescription = "Send",
                        tint = Color(0xFFE5DED5),
                        modifier = Modifier.size(14.dp)
                    )
                    Text("Send", color = Color(0xFFE5DED5), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Progress View during live file transmission.
 */
@Composable
private fun TransferProgressCard(
    progress: com.music.app.api.ShareTransferProgress?,
    statusMessage: String,
    connectedName: String?,
    isReceiver: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp)
            .border(
                BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                RoundedCornerShape(24.dp)
            ),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C222C))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = CircleShape,
                color = Color(0xFF2E3A46),
                modifier = Modifier.size(68.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (isReceiver) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_receive),
                            contentDescription = "Receive",
                            tint = Color(0xFFE5DED5),
                            modifier = Modifier.size(32.dp)
                        )
                    } else {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_paper_plane),
                            contentDescription = "Send",
                            tint = Color(0xFFE5DED5),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (isReceiver) "Receiving Music..." else "Sending Music...",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFE5DED5)
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Connected with ${connectedName ?: "Peer"}",
                fontSize = 13.sp,
                color = Color(0xFFA0AAB5)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Current Track Info
            if (progress != null) {
                Text(
                    text = progress.currentSongTitle,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFE5DED5),
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Track ${progress.currentItemIndex} of ${progress.totalItems}",
                    fontSize = 12.sp,
                    color = Color(0xFFA0AAB5)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Progress Bar
                LinearProgressIndicator(
                    progress = { progress.progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = Color(0xFFE5DED5),
                    trackColor = Color.White.copy(alpha = 0.12f)
                )

                Spacer(modifier = Modifier.height(8.dp))

                val mbTransferred = progress.bytesTransferred / (1024f * 1024f)
                val mbTotal = progress.totalBytes / (1024f * 1024f)
                val percent = (progress.progressFraction * 100).toInt()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${String.format("%.1f", mbTransferred)} MB / ${String.format("%.1f", mbTotal)} MB",
                        fontSize = 11.sp,
                        color = Color(0xFFA0AAB5)
                    )
                    Text(
                        text = "$percent%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE5DED5)
                    )
                }
            } else {
                Text(
                    text = statusMessage,
                    fontSize = 14.sp,
                    color = Color(0xFFE5DED5)
                )
            }
        }
    }
}

/**
 * Success completion view after transfer finishes.
 */
@Composable
private fun TransferSuccessView(
    message: String,
    onDone: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp)
            .border(
                BorderStroke(1.dp, Color(0xFF4ADE80).copy(alpha = 0.4f)),
                RoundedCornerShape(24.dp)
            ),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C222C))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = CircleShape,
                color = Color(0xFF2E3A46),
                modifier = Modifier.size(72.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("✅", fontSize = 36.sp)
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "Transfer Successful!",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFE5DED5)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = message,
                fontSize = 14.sp,
                color = Color(0xFFA0AAB5),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth(0.7f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4B5A69)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Done", color = Color(0xFFE5DED5), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

/**
 * Error view when transfer fails.
 */
@Composable
private fun TransferErrorView(
    errorMessage: String,
    onRetry: () -> Unit,
    onClose: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp)
            .border(
                BorderStroke(1.dp, Color(0xFFFF6B6B).copy(alpha = 0.4f)),
                RoundedCornerShape(24.dp)
            ),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C222C))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("⚠️", fontSize = 36.sp)
            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Transfer Failed",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFF6B6B)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = errorMessage,
                fontSize = 13.sp,
                color = Color(0xFFA0AAB5),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onClose,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                ) {
                    Text("Cancel", color = Color(0xFFE5DED5))
                }

                Button(
                    onClick = onRetry,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4B5A69))
                ) {
                    Text("Retry", color = Color(0xFFE5DED5), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
