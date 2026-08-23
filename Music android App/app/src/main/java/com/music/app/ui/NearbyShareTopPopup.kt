package com.music.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.app.R

/**
 * Top Popup Banner that slides down from top with rounded bottom corners.
 * Displays "Send" and "Receive" buttons for offline P2P music sharing.
 */
@Composable
fun NearbyShareTopPopup(
    visible: Boolean,
    onDismiss: () -> Unit,
    onSendClick: () -> Unit,
    onReceiveClick: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            initialOffsetY = { -it },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        ) + fadeIn(),
        exit = slideOutVertically(
            targetOffsetY = { -it },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        ) + fadeOut()
    ) {
        // Overlay scrim to tap outside to dismiss
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable { onDismiss() }
        ) {
            // Main Top Popup aligned to the top of screen
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .clickable(enabled = false) {} // Prevent click-through to background
                    .clip(
                        RoundedCornerShape(
                            bottomStart = 32.dp,
                            bottomEnd = 32.dp
                        )
                    )
                    .border(
                        BorderStroke(
                            1.dp,
                            Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.35f),
                                    Color.White.copy(alpha = 0.08f)
                                )
                            )
                        ),
                        RoundedCornerShape(
                            bottomStart = 32.dp,
                            bottomEnd = 32.dp
                        )
                    )
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { delta ->
                            if (delta < -15) {
                                onDismiss() // Swipe up to dismiss
                            }
                        }
                    ),
                color = Color.Transparent,
                shadowElevation = 24.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF14171F).copy(alpha = 0.96f))
                ) {
                    // Background image texture
                    Image(
                        painter = painterResource(id = R.drawable.background),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize(),
                        alpha = 0.18f
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Title & Subtitle
                        Text(
                            text = "Share Playlists & Songs",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE5DED5),
                            letterSpacing = (-0.3).sp
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "High-speed local Wi-Fi transfer with zero internet",
                            fontSize = 13.sp,
                            color = Color(0xFFA0AAB5)
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // Two Main Action Cards: SEND and RECEIVE
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // SEND / SHARE BUTTON
                            ShareActionButton(
                                modifier = Modifier.weight(1f),
                                title = "Send",
                                subtitle = "Share Playlists & Songs",
                                gradient = Brush.linearGradient(
                                    listOf(
                                        Color(0xFF4B5A69),
                                        Color(0xFF33404D)
                                    )
                                ),
                                iconContent = {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_paper_plane),
                                        contentDescription = "Send",
                                        tint = Color(0xFFE5DED5),
                                        modifier = Modifier.size(24.dp)
                                    )
                                },
                                onClick = onSendClick
                            )

                            // RECEIVE BUTTON
                            ShareActionButton(
                                modifier = Modifier.weight(1f),
                                title = "Receive",
                                subtitle = "Radar Discovery",
                                gradient = Brush.linearGradient(
                                    listOf(
                                        Color(0xFF2E3A46),
                                        Color(0xFF1B232B)
                                    )
                                ),
                                iconContent = {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_receive),
                                        contentDescription = "Receive",
                                        tint = Color(0xFFE5DED5),
                                        modifier = Modifier.size(24.dp)
                                    )
                                },
                                onClick = onReceiveClick
                            )
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // Bottom Swipe handle indicator
                        Box(
                            modifier = Modifier
                                .width(42.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color.White.copy(alpha = 0.28f))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShareActionButton(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    gradient: Brush,
    iconContent: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .clickable { onClick() }
            .border(
                BorderStroke(
                    1.dp,
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.30f),
                            Color.White.copy(alpha = 0.08f)
                        )
                    )
                ),
                RoundedCornerShape(22.dp)
            )
            .shadow(12.dp, RoundedCornerShape(22.dp)),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(gradient)
                .padding(horizontal = 16.dp, vertical = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.15f),
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        iconContent()
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE5DED5)
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = Color(0xFFA0AAB5),
                    maxLines = 1
                )
            }
        }
    }
}
