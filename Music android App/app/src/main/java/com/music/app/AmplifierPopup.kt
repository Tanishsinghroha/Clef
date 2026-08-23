package com.music.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Audio Amplifier & DSP Effect Controller Modal seamlessly themed with the app background and dark glassmorphic styling.
 */
@Composable
fun AmplifierPopup(
    onDismiss: () -> Unit
) {
    val isEnabled = AudioAmplifierController.isEnabled
    val volumeBoost = AudioAmplifierController.volumeBoost
    val bassBoost = AudioAmplifierController.bassBoost
    val virtualizer = AudioAmplifierController.virtualizerStrength
    val trebleBoost = AudioAmplifierController.trebleBoost
    val currentPreset = AudioAmplifierController.currentPreset
    val presets = AudioAmplifierController.presets

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(26.dp))
                .border(
                    BorderStroke(
                        1.dp,
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.25f),
                                Color.White.copy(alpha = 0.06f)
                            )
                        )
                    ),
                    RoundedCornerShape(26.dp)
                ),
            color = Color.Transparent,
            shadowElevation = 24.dp
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                // App background texture matching the main app
                Image(
                    painter = painterResource(id = R.drawable.background),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )

                // Dark frosted glass overlay matching the music player styling
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xFF0F0F1A).copy(alpha = 0.90f),
                                    Color(0xFF080810).copy(alpha = 0.95f)
                                )
                            )
                        )
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header with icon, title, master switch, and reset
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.12f))
                                    .border(
                                        BorderStroke(1.dp, Color.White.copy(alpha = 0.20f)),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = "⚡", fontSize = 17.sp)
                            }
                            Column {
                                Text(
                                    text = "Amplifier & FX",
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = if (isEnabled) "Studio Audio Engine" else "Bypassed",
                                    fontSize = 11.5.sp,
                                    color = if (isEnabled) Color(0xFF4ECCA3) else Color.Gray
                                )
                            }
                        }

                        Switch(
                            checked = isEnabled,
                            onCheckedChange = { AudioAmplifierController.setMasterEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color.White.copy(alpha = 0.40f),
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color.White.copy(alpha = 0.10f)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Equalizer Presets Row
                    Text(
                        text = "SOUND PROFILES",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray,
                        letterSpacing = 1.sp,
                        modifier = Modifier.align(Alignment.Start)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        presets.forEach { presetName ->
                            val isSelected = currentPreset == presetName
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(
                                        if (isSelected && isEnabled) {
                                            Color.White
                                        } else {
                                            Color.White.copy(alpha = 0.08f)
                                        }
                                    )
                                    .border(
                                        BorderStroke(
                                            1.dp,
                                            if (isSelected && isEnabled) Color.White else Color.White.copy(alpha = 0.14f)
                                        ),
                                        RoundedCornerShape(50)
                                    )
                                    .clickable {
                                        if (!isEnabled) AudioAmplifierController.setMasterEnabled(true)
                                        AudioAmplifierController.setPresetValue(presetName)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = presetName,
                                    fontSize = 12.5.sp,
                                    fontWeight = if (isSelected && isEnabled) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected && isEnabled) Color.Black else Color.White.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // 1. Volume Boost / Preamp Slider
                    AmplifierThemedSliderItem(
                        title = "Volume Boost (Preamp)",
                        subtitle = "Hardware Loudness Amplifier",
                        valueText = "+${(volumeBoost * 100).toInt()}% (+${String.format("%.1f", volumeBoost * 20)} dB)",
                        value = volumeBoost,
                        enabled = isEnabled,
                        onValueChange = { AudioAmplifierController.setVolumeBoostValue(it) }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // 2. Bass Boost Slider
                    AmplifierThemedSliderItem(
                        title = "Deep Bass Boost",
                        subtitle = "Sub-woofer punch & low-end depth",
                        valueText = "${(bassBoost * 100).toInt()}%",
                        value = bassBoost,
                        enabled = isEnabled,
                        onValueChange = { AudioAmplifierController.setBassBoostValue(it) }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // 3. 3D Virtualizer Slider
                    AmplifierThemedSliderItem(
                        title = "3D Spatial Virtualizer",
                        subtitle = "Surround sound stereo widening",
                        valueText = "${(virtualizer * 100).toInt()}%",
                        value = virtualizer,
                        enabled = isEnabled,
                        onValueChange = { AudioAmplifierController.setVirtualizerValue(it) }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // 4. Treble & Vocal Clarity Slider
                    AmplifierThemedSliderItem(
                        title = "Treble & Vocal Clarity",
                        subtitle = "Highs & crisp instrument separation",
                        valueText = "${(trebleBoost * 100).toInt()}%",
                        value = trebleBoost,
                        enabled = isEnabled,
                        onValueChange = { AudioAmplifierController.setTrebleBoostValue(it) }
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Close / Done Button matching app's primary white button style
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White
                        )
                    ) {
                        Text(
                            text = "Done",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AmplifierThemedSliderItem(
    title: String,
    subtitle: String,
    valueText: String,
    value: Float,
    enabled: Boolean,
    onValueChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(
                BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
                RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (enabled) Color.White else Color.Gray
                )
                Text(
                    text = subtitle,
                    fontSize = 10.5.sp,
                    color = Color.Gray
                )
            }
            Text(
                text = valueText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (enabled) Color.White else Color.Gray
            )
        }

        Slider(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = if (enabled) Color.White else Color.Gray,
                activeTrackColor = if (enabled) Color.White else Color.DarkGray,
                inactiveTrackColor = Color.White.copy(alpha = 0.12f)
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
